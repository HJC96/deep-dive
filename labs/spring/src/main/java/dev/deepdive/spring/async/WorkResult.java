package dev.deepdive.spring.async;

public record WorkResult(String taskName, String threadName) {

    public static WorkResult fromCurrentThread(String taskName) {
        return new WorkResult(taskName, Thread.currentThread().getName());
    }
}
