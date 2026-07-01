package dev.deepdive.actuator.notification;

import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PendingNotificationController {

    private final PendingNotificationManager pendingNotificationManager;

    public PendingNotificationController(PendingNotificationManager pendingNotificationManager) {
        this.pendingNotificationManager = pendingNotificationManager;
    }

    @PostMapping("/notifications/pending")
    public Map<String, Object> enqueue(@RequestParam(defaultValue = "pending notification") String message) {
        return Map.of("pendingSize", pendingNotificationManager.enqueue(message));
    }

    @DeleteMapping("/notifications/pending")
    public Map<String, Object> dequeue() {
        return Map.of("pendingSize", pendingNotificationManager.dequeue());
    }
}
