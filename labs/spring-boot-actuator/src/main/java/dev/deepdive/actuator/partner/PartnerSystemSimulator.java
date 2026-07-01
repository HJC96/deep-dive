package dev.deepdive.actuator.partner;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class PartnerSystemSimulator {

    // HealthIndicator가 읽는 외부 시스템 상태를 메모리로 단순화한 실습용 상태값
    private volatile boolean available = true;
    private volatile Instant changedAt = Instant.now();

    public boolean isAvailable() {
        return available;
    }

    public Instant changedAt() {
        return changedAt;
    }

    public void markAvailable() {
        // Postman에서 /partners/system/up 호출 시 health가 다시 UP으로 바뀌는 기준값
        available = true;
        changedAt = Instant.now();
    }

    public void markUnavailable() {
        // Postman에서 /partners/system/down 호출 시 health가 DOWN으로 바뀌는 기준값
        available = false;
        changedAt = Instant.now();
    }
}
