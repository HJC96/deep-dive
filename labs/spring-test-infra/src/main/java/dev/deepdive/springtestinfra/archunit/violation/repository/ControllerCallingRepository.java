package dev.deepdive.springtestinfra.archunit.violation.repository;

import dev.deepdive.springtestinfra.archunit.example.controller.SampleController;
import org.springframework.stereotype.Repository;

/**
 * 일부러 계층 규칙을 어긴 Repository. Repository가 Controller를 직접 호출한다.
 *
 * <p>정상 예제({@code archunit.example})와 섞이지 않도록 별도 패키지에 둔다. 계층 규칙을 통과해야 하는
 * {@code LayeredArchitectureTest}의 정상 케이스는 이 패키지를 임포트하지 않고, 위반 케이스만 이 클래스를
 * Repository 계층에 포함시켜 {@code check()}가 실제로 실패하는지 확인한다.
 */
@Repository
public class ControllerCallingRepository {

    private final SampleController sampleController;

    public ControllerCallingRepository(SampleController sampleController) {
        this.sampleController = sampleController;
    }

    public String findMessageThroughController() {
        return sampleController.sample();
    }
}
