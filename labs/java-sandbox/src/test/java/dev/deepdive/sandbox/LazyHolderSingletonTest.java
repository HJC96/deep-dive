package dev.deepdive.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class LazyHolderSingletonTest {

    @Test
    void 항상_같은_인스턴스를_반환한다() {
        LazyHolderSingleton first = LazyHolderSingleton.getInstance();
        LazyHolderSingleton second = LazyHolderSingleton.getInstance();

        assertThat(first).isSameAs(second);
    }

    @Test
    void 여러_스레드가_동시에_접근해도_인스턴스는_하나다() throws Exception {
        int threadCount = 100;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(16);

        // 반환된 인스턴스를 수집한다. 같은 인스턴스면 원소가 1개뿐이다.
        Set<LazyHolderSingleton> instances = ConcurrentHashMap.newKeySet();

        try {
            for (int i = 0; i < threadCount; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        instances.add(LazyHolderSingleton.getInstance());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            done.await();

            assertThat(instances).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
