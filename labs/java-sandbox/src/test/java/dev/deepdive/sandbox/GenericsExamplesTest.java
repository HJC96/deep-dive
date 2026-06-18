package dev.deepdive.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.deepdive.sandbox.GenericsExamples.Box;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenericsExamplesTest {

    @Test
    void 제네릭_클래스는_사용할_타입을_밖에서_정하게_한다() {
        Box<String> box = new Box<>("java");

        String value = box.value();

        assertThat(value).isEqualTo("java");
    }

    @Test
    void 제네릭_메서드는_호출부의_값으로_타입을_추론한다() {
        String firstName = GenericsExamples.first(List.of("java", "spring"));
        Integer firstScore = GenericsExamples.first(List.of(100, 90));

        assertThat(firstName).isEqualTo("java");
        assertThat(firstScore).isEqualTo(100);
    }

    @Test
    void 제네릭을_쓰면_잘못된_타입을_컴파일_시점에_막을_수_있다() {
        List<String> names = new ArrayList<>();

        names.add("java");
        // names.add(100); // 컴파일 에러: List<String>에는 String만 넣을 수 있다.

        assertThat(names.getFirst().toUpperCase()).isEqualTo("JAVA");
    }

    @Test
    void raw_type을_통해_잘못된_타입을_넣으면_꺼낼_때_ClassCastException이_발생한다() {
        List<String> names = new ArrayList<>();
        names.add("java");

        // List<String>을 raw List 파라미터로 넘기면 컴파일러가 String 전용 리스트라는 정보를 잃는다.
        // 그래서 Integer를 넣는 잘못된 코드가 컴파일 경고 수준으로 밀려나고, 실행은 되어 버린다.
        GenericsExamples.addIntegerByRawType(names);

        // 실제 리스트 안에는 String과 Integer가 함께 들어 있다.
        // Object로 꺼내면 캐스팅이 없으므로 값 자체를 확인할 수 있다.
        Object secondValue = names.get(1);
        assertThat(secondValue).isEqualTo(100);

        // 하지만 List<String>에서 값을 꺼내 String 변수에 담으면 컴파일러가 String 캐스팅 코드를 넣는다.
        // 실제 값은 Integer이므로 이 시점에 ClassCastException이 발생한다.
        assertThatThrownBy(() -> {
            String secondName = names.get(1);
            secondName.toUpperCase();
        }).isInstanceOf(ClassCastException.class);
    }

    @Test
    void extends_와일드카드는_Number_하위_타입_리스트에서_값을_읽을_때_쓴다() {
        List<Integer> integerScores = List.of(100, 90, 80);
        List<Long> longScores = List.of(10L, 20L, 30L);

        int integerTotal = GenericsExamples.sum(integerScores);
        int longTotal = GenericsExamples.sum(longScores);

        assertThat(integerTotal).isEqualTo(270);
        assertThat(longTotal).isEqualTo(60);
    }

    @Test
    void extends_와일드카드로_읽으면_Number가_상속해준_메서드를_그대로_쓸_수_있다() {
        // 원소의 실제 타입이 Integer/Long으로 서로 다르지만,
        // 둘 다 Number를 상속받았으므로 Number 타입으로 읽어 doubleValue() 같은
        // Number의 (상속된) 메서드를 동일하게 호출할 수 있다.
        List<Integer> integerScores = List.of(100, 90, 80);
        List<Long> longScores = List.of(10L, 20L, 30L);

        double integerAverage = GenericsExamples.averageAsDouble(integerScores);
        double longAverage = GenericsExamples.averageAsDouble(longScores);

        assertThat(integerAverage).isEqualTo(90.0);
        assertThat(longAverage).isEqualTo(20.0);
    }

    @Test
    void super_와일드카드는_Integer를_받아줄_수_있는_상위_타입_리스트에_값을_넣을_때_쓴다() {
        List<Integer> integers = new ArrayList<>();
        List<Number> numbers = new ArrayList<>();
        List<Object> objects = new ArrayList<>();

        GenericsExamples.addDefaultScores(integers);
        GenericsExamples.addDefaultScores(numbers);
        GenericsExamples.addDefaultScores(objects);

        assertThat(integers).containsExactly(100, 90);
        assertThat(numbers).containsExactly(100, 90);
        assertThat(objects).containsExactly(100, 90);
    }

    @Test
    void super_와일드카드로_읽으면_Object의_메서드까지만_쓸_수_있다() {
        // extends는 읽을 때 Number가 보장되어 Number의 상속 메서드를 쓸 수 있었지만,
        // super는 실제 타입이 List<Object>일 수도 있어 읽을 때 Object까지만 보장된다.
        // 그래서 호출할 수 있는 것은 Object가 가진 메서드(toString 등)뿐이다.
        List<Number> numbers = new ArrayList<>();
        numbers.add(42);
        List<Object> objects = new ArrayList<>();
        objects.add(7);

        assertThat(GenericsExamples.describeFirst(numbers)).isEqualTo("42");
        assertThat(GenericsExamples.describeFirst(objects)).isEqualTo("7");
    }
}
