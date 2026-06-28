# spring

Spring 자체 기능을 작게 분리해서 관찰하는 실험실.

## @Async vs ExecutorService

시작점:

- `SpringLabApplication`: `@EnableAsync`가 켜진 Spring Boot 애플리케이션
- `ExecutorConfig`: `@Async`가 사용할 `ThreadPoolTaskExecutor`와 직접 호출할 Java `ExecutorService`
- `AsyncWorkService`: `@Async` 어노테이션 기반 비동기 실행
- `ExecutorServiceWorkService`: Java `ExecutorService` 직접 제출 기반 비동기 실행
- `AsyncVsExecutorServiceTest`: 두 방식의 실행 스레드와 `@Async` self-invocation 한계 비교

실행:

```bash
./gradlew :spring:test
```

관찰 포인트:

- `@Async`는 Spring 프록시를 거쳐야 적용된다.
- `@Async` 실행 스레드는 `TaskExecutor` 설정을 따른다.
- `ExecutorService`는 코드에서 명시적으로 작업을 제출한다.
- 같은 클래스 내부에서 `this.doWork()`처럼 `@Async` 메서드를 호출하면 프록시를 거치지 않아 동기 실행된다.
