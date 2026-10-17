package dev.deepdive.springtestinfra.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LayeredArchitectureTest {

    private static final String BASE_PACKAGE = "dev.deepdive.springtestinfra.archunit.example";

    // 일부러 규칙을 어긴 클래스만 모아 둔 패키지. 정상 케이스의 임포트 대상에는 넣지 않는다.
    private static final String VIOLATION_PACKAGE = "dev.deepdive.springtestinfra.archunit.violation";

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages(BASE_PACKAGE + ".controller", BASE_PACKAGE + ".service", BASE_PACKAGE + ".repository");

    @Test
    void Controller_Service_Repository는_정해진_방향으로만_서로를_참조한다() {
        ArchRule rule = layeredArchitecture()
                .consideringAllDependencies()
                .layer("Controller").definedBy(BASE_PACKAGE + ".controller..")
                .layer("Service").definedBy(BASE_PACKAGE + ".service..")
                .layer("Repository").definedBy(BASE_PACKAGE + ".repository..")
                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
                .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service");

        assertThatCode(() -> rule.check(classes)).doesNotThrowAnyException();
    }

    @Test
    void Repository가_Controller를_호출하면_계층_규칙이_위반을_잡아낸다() {
        // 같은 규칙에 위반 예시 ControllerCallingRepository를 Repository 계층으로 함께 넣는다.
        // 규칙이 위반을 실제로 잡는지 확인해 두지 않으면, 위의 통과 테스트가 "검사할 게 없어서 통과"한 것인지 구분할 수 없다.
        JavaClasses classesWithViolation = new ClassFileImporter().importPackages(
                BASE_PACKAGE + ".controller", BASE_PACKAGE + ".service", BASE_PACKAGE + ".repository",
                VIOLATION_PACKAGE + ".repository");

        ArchRule rule = layeredArchitecture()
                .consideringAllDependencies()
                .layer("Controller").definedBy(BASE_PACKAGE + ".controller..")
                .layer("Service").definedBy(BASE_PACKAGE + ".service..")
                .layer("Repository").definedBy(BASE_PACKAGE + ".repository..", VIOLATION_PACKAGE + ".repository..")
                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
                .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service");

        assertThatThrownBy(() -> rule.check(classesWithViolation))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ControllerCallingRepository")
                .hasMessageContaining("SampleController");
    }
}
