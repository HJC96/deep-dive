package dev.deepdive.actuator.notification;

public record NotificationReceipt(
        String channel,
        String recipient,
        String message,
        String status
) {
}
