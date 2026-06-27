package dev.deepdive.actuator.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

// 클래스 이름이 LearningHealthIndicator라서 actuator health 응답에 learning이라는 컴포넌트로 들어감
@Component
public class LearningHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        return Health.up()
                .withDetail("topic", "spring-boot-actuator")
                .withDetail("message", "custom health indicator skeleton")
                .build();
    }
}
