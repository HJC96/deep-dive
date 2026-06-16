package dev.deepdive.sandbox;

public class LazyHolderSingleton {

    private LazyHolderSingleton() {
        // 외부 생성 차단
    }

    // 중첩 클래스는 getInstance()가 처음 호출될 때 비로소 로딩된다.
    // 클래스 로딩은 JVM이 보장하는 단 한 번뿐인 스레드 안전 작업이므로
    // synchronized 없이도 lazy 초기화 + 동시성 안전이 동시에 보장된다.
    private static class Holder {
        private static final LazyHolderSingleton INSTANCE = new LazyHolderSingleton();
    }

    public static LazyHolderSingleton getInstance() {
        return Holder.INSTANCE;
    }
}
