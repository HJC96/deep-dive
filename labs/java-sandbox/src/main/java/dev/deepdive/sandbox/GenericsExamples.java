package dev.deepdive.sandbox;

import java.util.List;

final class GenericsExamples {

    private GenericsExamples() {
    }

    static class Box<T> {

        private final T value;

        Box(T value) {
            this.value = value;
        }

        T value() {
            return value;
        }
    }

    static <T> T first(List<T> values) {
        return values.getFirst();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void addIntegerByRawType(List values) {
        values.add(100);
    }

    static int sum(List<? extends Number> values) {
        // ? extends Number는 "Number 또는 Number의 하위 타입 리스트"라는 뜻이다.
        // 그래서 List<Integer>, List<Long> 같은 리스트를 모두 받을 수 있다.
        //
        // 이 메서드 입장에서는 리스트가 값을 생산(produce)한다.
        // 값을 꺼내면 최소한 Number라는 것은 보장되므로 Number로 읽을 수 있다.
        int total = 0;
        for (Number value : values) {
            total += value.intValue();
        }

        // values.add(10); // 컴파일 에러
        // 실제 타입이 List<Integer>인지, List<Long>인지 모르므로 안전하게 값을 넣을 수 없다.
        return total;
    }

    static double averageAsDouble(List<? extends Number> values) {
        // 꺼낸 값의 정적 타입이 Number이므로, Number가 정의한 메서드는 무엇이든 쓸 수 있다.
        // intValue(), longValue(), doubleValue(), floatValue() 등이 모두 Number의 메서드다.
        //
        // 실제 원소가 Integer든 Long이든, 모두 Number를 상속받았기 때문에
        // Number 타입으로 읽으면 자식 타입에 상관없이 이 상속된 메서드들을 호출할 수 있다.
        double total = 0;
        for (Number value : values) {
            total += value.doubleValue();
        }
        return total / values.size();
    }

    static void addDefaultScores(List<? super Integer> values) {
        // ? super Integer는 "Integer 또는 Integer의 상위 타입 리스트"라는 뜻이다.
        // 그래서 List<Integer>, List<Number>, List<Object>를 모두 받을 수 있다.
        //
        // 이 메서드 입장에서는 리스트가 값을 소비(consume)한다.
        // Integer는 Integer/Number/Object 어디에나 들어갈 수 있으므로 안전하게 추가할 수 있다.
        values.add(100);
        values.add(90);

        // Integer score = values.getFirst(); // 컴파일 에러
        // 실제 타입이 List<Object>일 수도 있으므로, 꺼낼 때는 Object까지만 보장된다.
    }

    static String describeFirst(List<? super Integer> values) {
        // super 와일드카드 리스트에서 값을 읽으면, 정적 타입은 Object까지만 보장된다.
        // 실제 타입이 List<Object>일 수도 있으므로 Integer/Number라고 단정할 수 없기 때문이다.
        //
        // 그래서 extends와 달리 Number의 메서드(intValue 등)는 쓸 수 없고,
        // 모든 타입의 공통 조상인 Object의 메서드(toString 등)만 호출할 수 있다.
        Object first = values.getFirst();
        return first.toString();

        // int n = first.intValue(); // 컴파일 에러: Object에는 intValue()가 없다.
    }
}
