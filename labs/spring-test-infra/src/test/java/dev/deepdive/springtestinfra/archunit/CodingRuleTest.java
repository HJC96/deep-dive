package dev.deepdive.springtestinfra.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodingRuleTest {

    private static final String BASE_PACKAGE = "dev.deepdive.springtestinfra.archunit.example";

    @Test
    void 생성자_주입만_사용하는_패키지는_필드_주입_금지_규칙을_통과한다() {
        JavaClasses classes = new ClassFileImporter()
                .importPackages(BASE_PACKAGE + ".controller", BASE_PACKAGE + ".service", BASE_PACKAGE + ".repository");

        assertThatCode(() -> NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void 필드_주입을_사용하면_규칙이_위반을_잡아낸다() {
        JavaClasses classes = new ClassFileImporter().importPackages(BASE_PACKAGE + ".legacy");

        assertThatThrownBy(() -> NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(classes))
                .isInstanceOf(AssertionError.class);
    }
}
