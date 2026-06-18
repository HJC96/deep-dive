package dev.deepdive.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.deepdive.sandbox.CollectionFactoryMethodExamples.MutableKeyword;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CollectionFactoryMethodExamplesTest {

    /*
     * List.of(...), Set.of(...)는 "수정 불가능한 컬렉션"을 만드는 정적 팩토리 메서드다.
     *
     * 여기서 수정 불가능하다는 뜻은 add, remove, set 같은 컬렉션 구조 변경을 막는다는 뜻이다.
     * 요소 객체까지 깊게 복사하는 deep copy가 아니다.
     *
     * 즉, List.of(mutableObject)로 만든 리스트에 새 요소를 추가할 수는 없지만,
     * 리스트 안에 들어 있는 mutableObject 자체의 상태는 여전히 바뀔 수 있다.
     */

    @Test
    void List_of로_만든_리스트는_수정할_수_없다() {
        List<String> keywords = List.of("java", "spring");

        // List.of가 반환한 리스트 구현체는 구조 변경 메서드를 지원하지 않는다.
        // 그래서 add/remove/set 같은 메서드를 호출하면 UnsupportedOperationException이 발생한다.
        assertThat(keywords).isEqualTo(List.of("java", "spring"));
        assertThatThrownBy(() -> keywords.add("jpa"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void List_of는_요소_객체까지_deep_copy하지_않는다() {
        MutableKeyword keyword = new MutableKeyword("java");

        List<MutableKeyword> keywords = List.of(keyword);

        // 리스트 구조는 수정할 수 없다.
        assertThatThrownBy(() -> keywords.add(new MutableKeyword("spring")))
                .isInstanceOf(UnsupportedOperationException.class);

        // 하지만 리스트 안의 요소 객체는 같은 참조이므로, 요소 내부 상태는 바뀔 수 있다.
        // List.of는 컬렉션 구조를 immutable하게 만들 뿐, 요소 객체를 deep copy하지 않는다.
        keyword.rename("kotlin");

        assertThat(keywords.getFirst().name()).isEqualTo("kotlin");
    }

    @Test
    void new_ArrayList로_만든_리스트는_수정할_수_있다() {
        List<String> keywords = new ArrayList<>();

        keywords.add("java");
        keywords.add("spring");
        keywords.add("jpa");

        assertThat(keywords).isEqualTo(List.of("java", "spring", "jpa"));
    }

    @Test
    void List_of는_null을_허용하지_않지만_ArrayList는_null을_허용한다() {
        assertThatThrownBy(() -> List.of("java", null))
                .isInstanceOf(NullPointerException.class);

        List<String> keywords = new ArrayList<>();
        keywords.add("java");
        keywords.add(null);

        assertThat(keywords).hasSize(2);
    }

    @Test
    void Set_of로_만든_세트는_수정할_수_없고_중복_값을_허용하지_않는다() {
        Set<String> keywords = Set.of("java", "spring");

        assertThat(keywords).isEqualTo(Set.of("java", "spring"));
        assertThatThrownBy(() -> keywords.add("jpa"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> Set.of("java", "java"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void new_HashSet으로_만든_세트는_수정할_수_있고_중복_값은_하나로_합쳐진다() {
        Set<String> keywords = new HashSet<>();

        keywords.add("java");
        keywords.add("java");
        keywords.add("spring");

        assertThat(keywords).isEqualTo(Set.of("java", "spring"));
    }

    @Test
    void 초기값은_List_of로_만들고_수정이_필요하면_new_ArrayList로_감싼다() {
        List<String> keywords = new ArrayList<>(List.of("java", "spring"));

        keywords.add("jpa");

        assertThat(keywords).isEqualTo(List.of("java", "spring", "jpa"));
    }

    @Test
    void Arrays_asList는_크기_변경은_안되지만_값_교체는_가능하다() {
        String[] source = {"java", "spring"};
        List<String> keywords = Arrays.asList(source);

        keywords.set(0, "kotlin");

        assertThat(keywords).isEqualTo(List.of("kotlin", "spring"));
        assertThat(source[0]).isEqualTo("kotlin");
        assertThatThrownBy(() -> keywords.add("jpa"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void Collections_unmodifiableList는_수정_불가능한_view를_만든다() {
        List<String> source = new ArrayList<>(List.of("java", "spring"));
        List<String> readonlyView = Collections.unmodifiableList(source);

        source.add("jpa");

        assertThat(readonlyView).isEqualTo(List.of("java", "spring", "jpa"));
        assertThatThrownBy(() -> readonlyView.add("kotlin"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void Collections_unmodifiableList는_view이고_List_of는_별도_수정불가능_리스트다() {
        List<String> source = new ArrayList<>(List.of("java", "spring"));

        // unmodifiableList는 원본 리스트를 감싼 view다.
        // readonlyView 자체로 add/remove/set은 못 하지만, 원본 source가 바뀌면 readonlyView에도 반영된다.
        List<String> readonlyView = Collections.unmodifiableList(source);

        // List.of는 전달된 값들로 수정 불가능한 리스트를 새로 만든다.
        // source를 바라보는 view가 아니므로, 이후 source 변경에 영향을 받지 않는다.
        List<String> listOfValues = List.of(source.get(0), source.get(1));

        source.add("jpa");

        assertThat(readonlyView).containsExactly("java", "spring", "jpa");
        assertThat(listOfValues).containsExactly("java", "spring");
        assertThatThrownBy(() -> readonlyView.add("kotlin"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> listOfValues.add("kotlin"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void List_copyOf는_수정_불가능한_복사본을_만든다() {
        List<String> source = new ArrayList<>(List.of("java", "spring"));
        List<String> copied = List.copyOf(source);

        source.add("jpa");

        assertThat(copied).isEqualTo(List.of("java", "spring"));
        assertThatThrownBy(() -> copied.add("kotlin"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
