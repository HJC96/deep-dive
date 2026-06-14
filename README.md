# deep-dive

Java 동시성, 데이터 정합성, 인프라 연동 이슈를 작은 실험 단위로 쌓아가는 Gradle 기반 실험실입니다.

## 방향

- 특정 예제 하나에 종속되지 않는 구조로 시작합니다.
- 각 주제는 `labs/` 아래에 하나씩 추가합니다.
- 예를 들어 쿠폰 발급 동시성 문제는 다음 단계에서 `labs/coupon-issue`로 추가할 수 있습니다.
- DB 락, Redis 락, Redis 원자 연산 같은 해결 방식은 쿠폰 발급 lab 안에서 단계별 커밋으로 나눕니다.

## 현재 실험

첫 실험으로 쿠폰 발급 동시성 문제를 추가했습니다.

```text
deep-dive
├── build.gradle
├── settings.gradle
├── docs
└── labs
    └── coupon-issue
```

## Gradle

이 저장소는 Gradle을 사용합니다. 이유는 나중에 실험 주제가 늘어날 때 `labs/*`를 독립 모듈로 나누기 쉽고, Java 버전이나 공통 테스트 설정을 루트에서 관리하기 좋기 때문입니다.

기본 확인:

```bash
gradle projects
```

쿠폰 발급 실험 테스트:

```bash
gradle :coupon-issue:test
```

로컬 Gradle 캐시 문제로 실행이 실패하면 프로젝트 전용 Gradle home을 사용합니다.

```bash
GRADLE_USER_HOME=.gradle-home gradle projects
```
