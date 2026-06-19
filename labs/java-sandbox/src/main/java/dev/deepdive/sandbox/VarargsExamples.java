package dev.deepdive.sandbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class VarargsExamples {

    private VarargsExamples() {
    }

    static String join(String... values) {
        return String.join(",", values);
    }

    @SuppressWarnings({"unchecked", "varargs"})
    static String firstAfterHeapPollution(List<String>... values) {
        Object[] array = values;
        array[0] = List.of(100);

        return values[0].getFirst();
    }

    @SafeVarargs
    static <T> List<T> flatten(List<T>... values) {
        List<T> result = new ArrayList<>();

        for (List<T> value : values) {
            result.addAll(value);
        }

        return List.copyOf(result);
    }

    static class KeywordGroup {

        private final String[] keywords;

        private KeywordGroup(String[] keywords) {
            this.keywords = keywords;
        }

        static KeywordGroup unsafe(String... keywords) {
            return new KeywordGroup(keywords);
        }

        static KeywordGroup safe(String... keywords) {
            return new KeywordGroup(Arrays.copyOf(keywords, keywords.length));
        }

        String first() {
            return keywords[0];
        }
    }
}
