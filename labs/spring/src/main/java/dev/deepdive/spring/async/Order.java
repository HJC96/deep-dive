package dev.deepdive.spring.async;

public record Order(String id, String customerEmail, String productName, int totalPrice) {
}
