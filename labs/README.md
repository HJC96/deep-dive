# labs

주제별 실험 코드를 단계적으로 추가합니다.

[`index.html`](index.html)을 브라우저로 열면 모듈 전체 지도와 모듈별 `overview.html`로 이동할 수 있습니다.

## 목록

- `coupon-issue`: 동시성 문제 실험실
- `java-sandbox`: Java 실험실
- `cache-patterns`: 캐시 패턴 실험실
- `spring-cache-local`: Spring Cache 로컬 인메모리 캐시 실험실
- `spring-cache-redis`: Spring Cache Redis 실험실
- `spring-test-infra`: Spring 테스트 인프라 실험실
- `spring`: Spring 핵심 기능 실험실
- `seat-reservation`: 좌석 예약 동시성 실험실
- `spring-data-jpa`: Spring Data JPA 실험실
- `spring-boot-actuator`: Spring Boot Actuator 관찰 실험실
- `distributed-transaction-monolith`: 좌석·지갑·예약을 단일 DB 트랜잭션으로 처리하는 Monolithic 실험실
- `distributed-transaction-msa`: 서비스별 DB와 로컬 트랜잭션으로 부분 커밋을 재현하는 MSA 실험실
- `distributed-transaction-2pc`: MySQL `XA` SQL로 2단계 커밋을 직접 실행하는 실험실
- `distributed-transaction-tcc`: Try·Confirm·Cancel로 단계 사이에 락을 쥐지 않고 정합성을 맞추는 실험실
- `payment-system`: 결제 → 정산 → 장부를 Kafka로 잇는 흐름을 강의(인프런 「견고한 결제 시스템 구축」, 원본 Kotlin)를 Java로 옮기며 따라간 실험실
