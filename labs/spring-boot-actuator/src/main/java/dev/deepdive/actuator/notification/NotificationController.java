package dev.deepdive.actuator.notification;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/notifications/send")
    public NotificationReceipt send(
            @RequestParam(defaultValue = "email") String channel,
            @RequestParam(defaultValue = "user@example.com") String recipient,
            @RequestParam(defaultValue = "Actuator lab notification") String message
    ) {
        return notificationService.send(channel, recipient, message);
    }

    @PostMapping("/notifications/email")
    public NotificationReceipt email(
            @RequestParam(defaultValue = "user@example.com") String recipient,
            @RequestParam(defaultValue = "Email notification") String message
    ) {
        return notificationService.send("email", recipient, message);
    }

    @PostMapping("/notifications/sms")
    public NotificationReceipt sms(
            @RequestParam(defaultValue = "010-0000-0000") String recipient,
            @RequestParam(defaultValue = "SMS notification") String message
    ) {
        return notificationService.send("sms", recipient, message);
    }
}
