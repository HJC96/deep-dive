# deep-dive

공부하다 직접 돌려보고 싶은 내용을 정리하는 실험실. 주제 하나당 `labs/` 아래 모듈 하나.

## 모듈

- `coupon-issue` — 선착순 쿠폰 발급 동시성 문제. 락 없음 / synchronized / 원자적 UPDATE / 비관적 락 / 낙관적 락 / 네임드 락 / Redis 분산락 / Redis 원자적 차감(Lua) / Redisson 비교
- `java-sandbox` — AES 암호화 시 BouncyCastle Provider 등록 비용을 JMH로 측정하는 실험용 공간
- `cache-patterns` — Cache Aside / Read Through / Write Through / Write Around / Write Behind 캐시 패턴 비교
- `spring-cache-local` — Spring Cache를 로컬 인메모리 캐시로 사용할 때의 동작 실험
- `spring-cache-redis` — Spring Cache를 Redis 기반 공용 캐시로 사용할 때의 동작 실험. Cache Aside/Null Caching/TTL Jitter/Hot Key 방어를 `RedisTemplate`으로 직접 구현
- `spring-test-infra` — `@DynamicPropertySource`와 Testcontainers 기반 테스트 인프라 실험
- `spring` — `@Async`와 Java `ExecutorService` 비교 등 Spring 핵심 기능 실험
- `seat-reservation` — 좌석 예약 동시성, Redis 선점, Kafka 비동기 확정 실험
- `distributed-transaction-monolith` — 좌석·지갑·예약을 하나의 로컬 트랜잭션으로 처리하는 Monolithic 기준선
- `distributed-transaction-msa` — Reservation·Seat·Wallet을 분리하고 서비스별 로컬 트랜잭션의 부분 커밋을 재현하는 MSA 기준선
- `spring-data-jpa` — 낙관적 락과 Hibernate SQL 관찰 실험
- `spring-boot-actuator` — Actuator 기본 엔드포인트와 커스텀 헬스 체크 관찰

## 실행

```bash
./gradlew test                         # 전체 모듈
./gradlew :coupon-issue:test           # 쿠폰 동시성 모듈만
./gradlew :java-sandbox:test           # Java 실험 모듈만
./gradlew :cache-patterns:test         # 캐시 패턴 모듈만
./gradlew :spring-cache-local:test     # Spring Cache 로컬 캐시 모듈만
./gradlew :spring-cache-redis:test     # Spring Cache Redis 모듈만
./gradlew :spring-test-infra:test      # Spring 테스트 인프라 모듈만
./gradlew :spring:test                 # Spring 핵심 기능 모듈만
./gradlew :seat-reservation:test       # 좌석 예약 모듈만
./gradlew :spring-data-jpa:test        # Spring Data JPA 모듈만
./gradlew :spring-boot-actuator:test   # Actuator 모듈만
```
