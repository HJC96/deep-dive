package dev.deepdive.springtestinfra.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThatCode;

class LayeredArchitectureTest {

    private static final String BASE_PACKAGE = "dev.deepdive.springtestinfra.archunit.example";

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
}
