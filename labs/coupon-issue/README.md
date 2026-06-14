# coupon-issue

쿠폰 발급 과정에서 여러 요청이 동시에 들어올 때 발급 수량과 재고 수량이 어긋나는 문제를 실험합니다.

첫 단계는 락, 트랜잭션 제어, Redis 없이 같은 쿠폰 재고를 동시에 차감하면 왜 결과가 깨지는지 확인하는 것입니다.

동시성 문제는 쿠폰이 제한 수량보다 많이 발급되는 경우뿐 아니라, 발급 성공 수와 재고 차감 수가 서로 다르게 기록되는 경우도 포함합니다.

## 케이스 1. 동시에 재고 차감

시나리오:

- 쿠폰 ID `1L`의 재고가 200개 있습니다.
- 200개의 요청이 동시에 쿠폰 발급 서비스를 호출합니다.

기대 결과:

- `200 - 200 = 0`

실제 결과:

- 여러 스레드가 같은 재고 값을 읽고 각각 차감하면, 일부 차감 결과가 덮어써질 수 있습니다.
- 따라서 최종 재고가 `0`이 아닐 수 있습니다.

```mermaid
sequenceDiagram
    autonumber
    participant Test as 테스트 스레드
    participant Executor as ExecutorService
    participant StartLatch as start CountDownLatch
    participant DoneLatch as done CountDownLatch
    participant ThreadA as 요청 스레드 A
    participant ThreadB as 요청 스레드 B
    participant Service as CouponIssueService
    participant Repository as CouponRepository
    participant Stock as CouponStock (remainingQuantity = 200)

    Test->>Executor: 200개 차감 작업 제출
    Executor->>ThreadA: 작업 할당
    Executor->>ThreadB: 작업 할당
    ThreadA->>StartLatch: await()
    ThreadB->>StartLatch: await()

    Test->>StartLatch: countDown()
    StartLatch-->>ThreadA: 대기 해제
    StartLatch-->>ThreadB: 대기 해제

    ThreadA->>Service: issue(1L)
    Service->>Repository: findById(1L)
    Repository-->>Service: CouponStock
    Service->>Stock: decrease()
    Stock-->>ThreadA: currentQuantity = 200 읽기

    ThreadB->>Service: issue(1L)
    Service->>Repository: findById(1L)
    Repository-->>Service: CouponStock
    Service->>Stock: decrease()
    Stock-->>ThreadB: currentQuantity = 200 읽기

    ThreadA->>ThreadA: 200 - 1 계산
    ThreadB->>ThreadB: 200 - 1 계산

    ThreadA->>Stock: remainingQuantity = 199 저장
    ThreadB->>Stock: remainingQuantity = 199 저장

    ThreadA->>DoneLatch: countDown()
    ThreadB->>DoneLatch: countDown()
    Test->>DoneLatch: await()
    DoneLatch-->>Test: 모든 작업 완료

    Note over Stock: 두 요청이 모두 차감했지만 최종 재고는 198이 아니라 199
    Note over Test,Stock: 이런 덮어쓰기(lost update)가 반복되면 200번 차감 후에도 재고가 0이 아닐 수 있음
```
