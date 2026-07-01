package dev.deepdive.actuator.health;

import dev.deepdive.actuator.partner.PartnerSystemSimulator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class PartnerSystemHealthIndicator implements HealthIndicator {

    private final PartnerSystemSimulator partnerSystem;

    public PartnerSystemHealthIndicator(PartnerSystemSimulator partnerSystem) {
        this.partnerSystem = partnerSystem;
    }

    @Override
    public Health health() {
        // HealthIndicator Bean 이름에서 Indicator를 뺀 partnerSystem이 actuator component id가 된다.
        if (partnerSystem.isAvailable()) {
            return Health.up()
                    .withDetail("system", "partner-notification-gateway")
                    .withDetail("checkedAt", partnerSystem.changedAt())
                    .withDetail("message", "Partner system is reachable")
                    .build();
        }

        // DOWN을 반환하면 health endpoint의 HTTP status도 기본적으로 503으로 내려간다.
        return Health.down()
                .withDetail("system", "partner-notification-gateway")
                .withDetail("checkedAt", partnerSystem.changedAt())
                .withDetail("message", "Partner system is unavailable")
                .build();
    }
}
