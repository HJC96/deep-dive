package dev.deepdive.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.deepdive.sandbox.VarargsExamples.KeywordGroup;
import java.util.List;
import org.junit.jupiter.api.Test;

class VarargsExamplesTest {

    /*
     * 제네릭 + 가변인자
     *
     * 가변인자는 컴파일 후 배열로 다뤄진다.
     * 그런데 제네릭 타입 정보는 런타임에 지워지므로 List<String>... 같은 코드는 heap pollution 위험이 있다.
     *
     * 안전한 제네릭 가변인자 메서드라면 @SafeVarargs로
     * "이 메서드는 가변인자 배열에 잘못된 값을 넣거나 외부로 노출하지 않는다"는 의도를 표현한다.
     */

    @Test
    void 가변인자는_여러_값을_쉼표로_넘길_수_있다() {
        String result = VarargsExamples.join("java", "spring", "jpa");

        assertThat(result).isEqualTo("java,spring,jpa");
    }

    @Test
    void 가변인자에는_배열도_그대로_넘길_수_있다() {
        String[] keywords = {"java", "spring", "jpa"};

        String result = VarargsExamples.join(keywords);

        assertThat(result).isEqualTo("java,spring,jpa");
    }

    @SuppressWarnings("unchecked")
    @Test
    void 제네릭_가변인자는_배열을_통해_잘못된_타입이_섞일_수_있다() {
        List<String> keywords = List.of("java", "spring");

        assertThatThrownBy(() -> VarargsExamples.firstAfterHeapPollution(keywords))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void 안전한_제네릭_가변인자_메서드는_SafeVarargs로_의도를_표현한다() {
        List<String> keywords = VarargsExamples.flatten(
                List.of("java", "spring"),
                List.of("jpa")
        );

        assertThat(keywords).containsExactly("java", "spring", "jpa");
    }

    @Test
    void 가변인자로_받은_배열을_그대로_저장하면_외부_배열_변경에_영향을_받는다() {
        String[] keywords = {"java", "spring"};

        KeywordGroup group = KeywordGroup.unsafe(keywords);

        keywords[0] = "kotlin";

        assertThat(group.first()).isEqualTo("kotlin");
    }

    @Test
    void 가변인자로_받은_배열을_저장할_때는_복사해서_보호할_수_있다() {
        String[] keywords = {"java", "spring"};

        KeywordGroup group = KeywordGroup.safe(keywords);

        keywords[0] = "kotlin";

        assertThat(group.first()).isEqualTo("java");
    }
}
