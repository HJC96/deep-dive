package dev.deepdive.actuator.notification;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PendingNotificationManager {

    private final List<String> pendingNotifications = Collections.synchronizedList(new ArrayList<>());

    public PendingNotificationManager(MeterRegistry meterRegistry) {
        // Gauge는 값을 저장하지 않고 actuator 조회 시점에 List::size를 호출한다.
        Gauge.builder("app.notification.pending.size", pendingNotifications, List::size)
                .description("현재 발송 대기 중인 알림 수")
                .register(meterRegistry);
    }

    public int enqueue(String message) {
        pendingNotifications.add(message);
        return pendingNotifications.size();
    }

    public int dequeue() {
        if (!pendingNotifications.isEmpty()) {
            pendingNotifications.removeFirst();
        }
        return pendingNotifications.size();
    }
}
