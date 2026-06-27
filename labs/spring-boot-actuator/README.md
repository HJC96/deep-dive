# spring-boot-actuator

Spring Boot Actuator의 기본 엔드포인트와 커스텀 헬스 체크를 관찰하기 위한 최소 학습 모듈입니다.

## 포함된 것

- `/actuator/health`: 기본 헬스 상태와 `LearningHealthIndicator`가 추가한 커스텀 상세 정보
- `/actuator/info`: `application.yml`에 둔 앱 메타데이터
- `/actuator/metrics`: JVM, HTTP, 프로세스 관련 기본 메트릭

## 실행

```bash
./gradlew :spring-boot-actuator:run
```

실행 후 curl 스크립트로 actuator 엔드포인트를 한번에 호출합니다.

```bash
./labs/spring-boot-actuator/scripts/curl-actuator.sh
```

특정 주소로 호출하려면:

```bash
BASE_URL=http://localhost:18080 ./labs/spring-boot-actuator/scripts/curl-actuator.sh
```

개별로 확인하려면:

```bash
curl http://localhost:18080/actuator/health
curl http://localhost:18080/actuator/info
curl http://localhost:18080/actuator/metrics
```

## 다음 실험 후보

- health group으로 readiness/liveness 나누기
- management server port 분리하기
- `@Endpoint`로 커스텀 actuator endpoint 만들기
- Micrometer registry를 붙여 메트릭 수집 흐름 보기
