# coupon-issue

선착순 쿠폰 발급에서 같은 재고를 동시에 차감할 때 생기는 동시성 문제 실험.

비교 대상:

- 동시성 제어 없음
- `synchronized`
- 원자적 `UPDATE`
- 비관적 락 (`SELECT ... FOR UPDATE`)
- 낙관적 락 (`@Version`)

## 공통 환경

- Testcontainers MySQL 8.0.36
- Spring Data JPA `CouponRepository` (`coupon_stock` 테이블)
- 낙관적 락만 `@Version`을 가진 별도 엔티티 `VersionedCouponStock`(`versioned_coupon_stock` 테이블) + `VersionedCouponRepository` 사용
  - `@Version`이 모든 발급 경로에 영향을 주지 않도록 엔티티를 분리한다. 같은 `CouponStock`에 `@Version`을 두면 "락 없음" 케이스마저 커밋 시 버전 검사를 받아 `ObjectOptimisticLockingFailureException`이 나고 lost update가 재현되지 않는다.
- 테스트 시작 전 `coupon_stock`, `versioned_coupon_stock` 초기화
- 실험 대상 쿠폰: ID `1L`, 재고 200개
- 요청 수: 200개
- 동시 시작: `start` 래치
- 종료 대기: `done` 래치
- 실행 시간: 테스트 콘솔에 `ms` 출력

## 케이스 1. 동시성 제어 없음

흐름:

```text
SELECT -> 메모리에서 -1 -> flush/commit 시 UPDATE
```

코드 흐름:

```java
@Transactional
public void issue(Long couponId) {
    CouponStock stock = couponRepository.findById(couponId).orElseThrow();
    stock.decrease();
    couponRepository.save(stock);
}
```

결과:

- 기대값: `200 - 200 = 0`
- 실제 결과: 최종 재고가 `0`이 아닐 수 있음
- 테스트 단언: `isNotEqualTo(0)`

이유:

- 두 스레드가 둘 다 `quantity = 200` 조회
- 둘 다 메모리에서 `199`로 계산
- 둘 다 DB에 `quantity = 199` 저장
- 요청은 2번 처리됐지만 실제 차감은 1번만 반영
- lost update 발생

```mermaid
sequenceDiagram
    autonumber
    participant Test as 테스트 스레드
    participant Executor as ExecutorService
    participant StartLatch as start CountDownLatch
    participant DoneLatch as done CountDownLatch
    participant A as 요청 A
    participant B as 요청 B
    participant Service as CouponIssueService
    participant Repository as CouponRepository
    participant DB as "MySQL(quantity = 200)"

    Test->>Executor: 200개 발급 작업 제출
    Executor->>A: 작업 할당
    Executor->>B: 작업 할당
    A->>StartLatch: await()
    B->>StartLatch: await()

    Test->>StartLatch: countDown()
    StartLatch-->>A: 대기 해제
    StartLatch-->>B: 대기 해제

    A->>Service: issue(1L)
    Service->>Repository: findById(1L)
    Repository->>DB: SELECT quantity
    DB-->>A: 200

    B->>Service: issue(1L)
    Service->>Repository: findById(1L)
    Repository->>DB: SELECT quantity
    DB-->>B: 200

    A->>A: 200 - 1 = 199
    B->>B: 200 - 1 = 199

    A->>DB: UPDATE quantity = 199
    B->>DB: UPDATE quantity = 199

    A->>DoneLatch: countDown()
    B->>DoneLatch: countDown()
    Test->>DoneLatch: await()
    DoneLatch-->>Test: 모든 작업 완료

    Note over DB: 두 요청 처리 후 최종 재고는 198이 아니라 199
```

주의:

- `@Transactional`은 Spring 프록시를 거쳐야 동작
- `CouponIssueService`는 테스트에서 직접 `new` 하지 않고 Spring 빈으로 주입

## 케이스 2. synchronized

흐름:

```text
synchronized 진입 -> SELECT -> 메모리에서 -1 -> flush/commit 시 UPDATE -> synchronized 종료
```

코드 흐름:

```java
public synchronized void issue(Long couponId) {
    CouponStock stock = couponRepository.findById(couponId).orElseThrow();
    stock.decrease();
    couponRepository.save(stock);
}
```

결과:

- 최종 재고: `0`
- 테스트 단언: `isEqualTo(0)`

이유:

- 한 스레드만 `issue()` 진입
- 읽기, 계산, 저장이 순서대로 처리
- lost update 없음

한계:

- 같은 서비스 인스턴스/단일 JVM 안에서만 유효
- 서버 여러 대면 각 JVM이 별도 모니터 락 사용
- 전체 구간 직렬화로 처리량 저하

```mermaid
sequenceDiagram
    autonumber
    participant Test as 테스트 스레드
    participant Executor as ExecutorService
    participant StartLatch as start CountDownLatch
    participant DoneLatch as done CountDownLatch
    participant A as 요청 A
    participant B as 요청 B
    participant Service as SynchronizedCouponIssueService
    participant DB as "MySQL(quantity = 200)"

    Test->>Executor: 200개 발급 작업 제출
    Executor->>A: 작업 할당
    Executor->>B: 작업 할당
    A->>StartLatch: await()
    B->>StartLatch: await()

    Test->>StartLatch: countDown()
    StartLatch-->>A: 대기 해제
    StartLatch-->>B: 대기 해제

    A->>Service: issue(1L)
    activate Service
    Note over A,Service: 모니터 락 획득
    Service->>DB: SELECT quantity = 200
    Service->>Service: 200 - 1 = 199
    Service->>DB: UPDATE quantity = 199
    deactivate Service
    A->>DoneLatch: countDown()

    B->>Service: issue(1L)
    activate Service
    Note over B,Service: A가 끝난 뒤 진입
    Service->>DB: SELECT quantity = 199
    Service->>Service: 199 - 1 = 198
    Service->>DB: UPDATE quantity = 198
    deactivate Service
    B->>DoneLatch: countDown()
    Test->>DoneLatch: await()
    DoneLatch-->>Test: 모든 작업 완료

    Note over DB: 요청이 순서대로 처리되어 재고가 정확히 차감
```

## 케이스 3. 원자적 UPDATE

흐름:

```text
UPDATE 한 방으로 조건 검사 + 차감
```

쿼리:

```java
@Modifying
@Query("""
        UPDATE CouponStock c
        SET c.remainingQuantity = c.remainingQuantity - 1
        WHERE c.couponId = :couponId AND c.remainingQuantity >= 1
        """)
int decreaseQuantity(@Param("couponId") Long couponId);
```

결과:

- 이번 테스트의 성공 발급 수: 200개
- 최종 재고: `0`
- 테스트 단언: `issuedCount == 200`, `remainingQuantity == 0`

이유:

- `quantity >= 1` 검사와 `quantity - 1` 차감을 한 SQL에서 처리
- MySQL이 같은 row에 대한 `UPDATE`를 안전하게 처리
- 애플리케이션 레벨 락 불필요
- `affected rows = 1`: 성공
- `affected rows = 0`: 재고 부족
- 현재 테스트는 요청 수와 재고가 같아서 `0` 케이스까지 직접 검증하지 않음

한계:

- 단순 증감에 적합
- 여러 테이블 검증, 복잡한 정책, 외부 조건이 섞이면 SQL 한 문장으로 끝내기 어려움

```mermaid
sequenceDiagram
    autonumber
    participant Test as 테스트 스레드
    participant Executor as ExecutorService
    participant StartLatch as start CountDownLatch
    participant DoneLatch as done CountDownLatch
    participant A as 요청 A
    participant B as 요청 B
    participant Service as AtomicUpdateCouponIssueService
    participant Repository as CouponRepository
    participant DB as "MySQL(quantity = 200)"

    Test->>Executor: 200개 발급 작업 제출
    Executor->>A: 작업 할당
    Executor->>B: 작업 할당
    A->>StartLatch: await()
    B->>StartLatch: await()

    Test->>StartLatch: countDown()
    StartLatch-->>A: 대기 해제
    StartLatch-->>B: 대기 해제

    A->>Service: issue(1L)
    Service->>Repository: decreaseQuantity(1L)
    Repository->>DB: UPDATE quantity = quantity - 1 WHERE id = 1 AND quantity >= 1
    DB-->>Repository: affected rows = 1
    Repository-->>Service: true

    B->>Service: issue(1L)
    Service->>Repository: decreaseQuantity(1L)
    Repository->>DB: UPDATE quantity = quantity - 1 WHERE id = 1 AND quantity >= 1
    DB-->>Repository: affected rows = 1
    Repository-->>Service: true

    A->>DoneLatch: countDown()
    B->>DoneLatch: countDown()
    Test->>DoneLatch: await()
    DoneLatch-->>Test: 모든 작업 완료

    Note over DB: DB가 단일 UPDATE를 원자적으로 처리
```

## 케이스 4. 비관적 락 (`SELECT ... FOR UPDATE`)

흐름:

```text
SELECT ... FOR UPDATE(row 잠금) -> 메모리에서 -1 -> commit 시 UPDATE -> 락 해제
다른 트랜잭션은 락이 풀릴 때까지 대기
```

쿼리:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        SELECT c
        FROM CouponStock c
        WHERE c.couponId = :couponId
        """)
Optional<CouponStock> findByIdWithPessimisticLock(@Param("couponId") Long couponId);
```

코드 흐름:

```java
@Transactional
public void issue(Long couponId) {
    CouponStock stock = couponRepository.findByIdWithPessimisticLock(couponId).orElseThrow();
    stock.decrease();
    couponRepository.save(stock);
}
```

결과:

- 최종 재고: `0`
- 테스트 단언: `isEqualTo(0)`

이유:

- `PESSIMISTIC_WRITE`는 조회 시점에 해당 row에 쓰기 락(`SELECT ... FOR UPDATE`)을 건다.
- 락을 잡은 트랜잭션이 커밋/롤백할 때까지 다른 트랜잭션의 같은 row 조회는 대기한다.
- 읽기-계산-쓰기 구간이 트랜잭션 단위로 직렬화되므로 lost update가 없다.
- `synchronized`와 달리 애플리케이션이 아니라 **DB가 락을 관리**하므로, 서버가 여러 대여도 동작한다.

한계:

- 전 구간을 직렬화하므로 동시 요청이 많이 몰리면 처리량이 떨어진다.
- 락 대기가 길어지면 커넥션을 오래 점유하고, 최악의 경우 락 타임아웃·데드락이 생길 수 있다.
- 락을 잡는 동안 커넥션 풀을 물고 있어, 동시 요청이 많으면 풀 고갈 위험이 있다.

```mermaid
sequenceDiagram
    autonumber
    participant Test as 테스트 스레드
    participant Executor as ExecutorService
    participant StartLatch as start CountDownLatch
    participant DoneLatch as done CountDownLatch
    participant A as 요청 A
    participant B as 요청 B
    participant Service as PessimisticLockCouponIssueService
    participant DB as "MySQL(quantity = 200)"

    Test->>Executor: 200개 발급 작업 제출
    Executor->>A: 작업 할당
    Executor->>B: 작업 할당
    A->>StartLatch: await()
    B->>StartLatch: await()

    Test->>StartLatch: countDown()
    StartLatch-->>A: 대기 해제
    StartLatch-->>B: 대기 해제

    A->>Service: issue(1L)
    activate Service
    Note over A,DB: A가 row 락 획득 (SELECT ... FOR UPDATE)
    Service->>DB: SELECT quantity = 200 FOR UPDATE
    B->>Service: issue(1L)
    Note over B,DB: B는 같은 row 락 대기
    Service->>Service: 200 - 1 = 199
    Service->>DB: UPDATE quantity = 199
    Note over A,DB: A 커밋 → 락 해제
    deactivate Service
    A->>DoneLatch: countDown()

    activate Service
    Note over B,DB: B가 락 획득
    Service->>DB: SELECT quantity = 199 FOR UPDATE
    Service->>Service: 199 - 1 = 198
    Service->>DB: UPDATE quantity = 198
    deactivate Service
    B->>DoneLatch: countDown()
    Test->>DoneLatch: await()
    DoneLatch-->>Test: 모든 작업 완료

    Note over DB: 락으로 직렬화되어 재고가 정확히 차감
```

## 케이스 5. 낙관적 락 (`@Version`)

흐름:

```text
SELECT(version 포함) -> 메모리에서 -1 -> commit 시 UPDATE ... WHERE version = ?
  -> version 안 맞으면 예외 -> 잠깐 대기 후 처음부터 재시도
```

버전 컬럼은 **전용 엔티티** `VersionedCouponStock`에만 둔다. 다른 발급 방식이 쓰는 `CouponStock`에는 `@Version`이 없다:

```java
@Entity
@Table(name = "versioned_coupon_stock")
public class VersionedCouponStock {
    @Version
    private Long version;
    // ...
}
```

코드 흐름:

```java
// 재시도는 트랜잭션 "바깥"에 있어야 한다.
public void issue(Long couponId) {
    while (true) {
        try {
            self.issueOnce(couponId);   // 프록시를 통해 @Transactional 적용
            return;
        } catch (ObjectOptimisticLockingFailureException conflict) {
            sleep(5);                   // 잠깐 기다렸다 재시도
        }
    }
}

@Transactional
public void issueOnce(Long couponId) {
    VersionedCouponStock stock = versionedCouponRepository.findByIdWithOptimisticLock(couponId).orElseThrow();
    stock.decrease();
    versionedCouponRepository.save(stock);
}
```

> `@Version`을 공용 `CouponStock`에 두면 "락 없음" 케이스까지 커밋 시 버전 검사를 받아 lost update를 재현하지 못한다. 그래서 낙관적 락만 별도 엔티티/리포지토리로 분리했다.

결과:

- 최종 재고: `0`
- 테스트 단언: `isEqualTo(0)`

이유:

- 읽을 때 `version`을 함께 가져온다.
- 커밋 시 `UPDATE ... WHERE version = 읽은값`으로 갱신한다.
- 그 사이 다른 트랜잭션이 먼저 커밋해 `version`이 바뀌었으면 `affected rows = 0` → `ObjectOptimisticLockingFailureException`.
- 충돌한 요청은 **실패**하므로, 재시도 없이는 재고가 `0`까지 줄지 않는다.
- 재시도 루프가 충돌한 요청을 새 트랜잭션에서 다시 시도해 결국 모두 성공시킨다.

한계 (낙관적 락의 단점):

- **충돌이 곧 실패다.** 락으로 막는 게 아니라 "충돌했음을 사후에 감지"하는 방식이라, 충돌하면 예외가 난다.
- **재시도 로직이 사실상 필수다.** 충돌을 그냥 두면 요청이 실패하므로, 호출 측이나 서비스가 재시도(+백오프)를 직접 구현해야 한다. 비관적 락·원자적 `UPDATE`에는 없는 추가 코드다.
- **재시도가 트랜잭션 바깥에 있어야 한다.** 충돌 예외는 커밋 시점에 나고 그 트랜잭션은 롤백되므로, 같은 `@Transactional` 안에서 잡아 재시도할 수 없다. 그래서 트랜잭션 경계와 재시도 루프를 분리하게 된다.
- **동시 요청이 몰리면 비효율적이다.** 같은 row를 여러 스레드가 동시에 다투면 대부분 충돌 → 재시도가 폭주한다. 이 실험에서도 200개를 한 row에 몰아 재시도가 쌓이면서 다른 방식보다 훨씬 느리다(아래 실행 시간 참고). 동시 충돌이 드문 상황에 적합한 전략이다.

```mermaid
sequenceDiagram
    autonumber
    participant Test as 테스트 스레드
    participant Executor as ExecutorService
    participant StartLatch as start CountDownLatch
    participant DoneLatch as done CountDownLatch
    participant A as 요청 A
    participant B as 요청 B
    participant Service as OptimisticLockCouponIssueService
    participant DB as "MySQL(quantity = 200, version = 0)"

    Test->>Executor: 200개 발급 작업 제출
    Executor->>A: 작업 할당
    Executor->>B: 작업 할당
    A->>StartLatch: await()
    B->>StartLatch: await()

    Test->>StartLatch: countDown()
    StartLatch-->>A: 대기 해제
    StartLatch-->>B: 대기 해제

    A->>Service: issue(1L)
    B->>Service: issue(1L)

    Service->>DB: A) SELECT quantity=200, version=0
    Service->>DB: B) SELECT quantity=200, version=0

    Service->>DB: A) UPDATE quantity=199 WHERE version=0
    Note over DB: A 성공, version=1
    Service->>DB: B) UPDATE quantity=199 WHERE version=0
    DB-->>Service: B) affected rows = 0

    Note over Service: B는 ObjectOptimisticLockingFailureException
    Service->>Service: B) sleep(5ms) 후 재시도
    Service->>DB: B) SELECT quantity=199, version=1
    Service->>DB: B) UPDATE quantity=198 WHERE version=1
    Note over DB: B 성공, version=2

    A->>DoneLatch: countDown()
    B->>DoneLatch: countDown()
    Test->>DoneLatch: await()
    DoneLatch-->>Test: 모든 작업 완료

    Note over DB: 재시도 덕분에 두 요청 모두 반영
```

주의 (재시도 + `self` 호출):

- `@Transactional` 빈은 Spring이 **프록시로 한 겹 감싼다.** 트랜잭션을 열고 커밋/롤백하는 건 알맹이(원본 객체)가 아니라 이 프록시다.
- 충돌(=커밋)도 프록시가 메서드를 끝낸 뒤에 처리한다. 한 번 충돌하면 그 트랜잭션은 끝난것이고 재시도란 곧 **트랜잭션을 새로 여는 것**이다
- 그래서 **시도 1회 = 트랜잭션 1개**가 되도록, 한 번의 시도를 `issueOnce`(`@Transactional`)로 두고 → 그걸 반복(재시도)하는 루프 `issue`는 트랜잭션 밖에 둔다
- `this.issueOnce()`는 알맹이가 자기 메서드를 직접 부르는 것이라 프록시를 건너뛴다 → 트랜잭션이 안 열림. 프록시 참조인 `self`로 호출해야 한다
- `self`는 자기 자신을 주입받아 확보 (순환 참조 회피용 `@Lazy`)
- 재시도를 별도 클래스(퍼사드)로 빼면 `self`/`@Lazy` 없이도 된다. 퍼사드가 주입받은 서비스는 **이미 프록시**라(Spring이 다른 빈엔 프록시를 넣어줌), 그냥 호출해도 프록시를 거쳐 트랜잭션이 열리기 때문. 여기선 한 클래스로 보여주려고 `self`를 씀

## 실행 시간

예시 출력:

```text
락 없음: 95.219ms
synchronized: 936.464ms
원자적 UPDATE: 310.832ms
비관적 락: 365.205ms
낙관적 락(재시도): 2542.703ms
```

> 낙관적 락이 가장 느린 이유: 200개 요청이 한 row를 다투면서 충돌 → 재시도가 반복되기 때문이다. 이렇게 동시 요청이 한꺼번에 몰리는 선착순 쿠폰 같은 상황에는 잘 맞지 않는다.
