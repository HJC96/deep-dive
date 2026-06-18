package dev.deepdive.sandbox;

final class CollectionFactoryMethodExamples {

    private CollectionFactoryMethodExamples() {
    }

    static class MutableKeyword {

        private String name;

        MutableKeyword(String name) {
            this.name = name;
        }

        void rename(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }
    }
}
