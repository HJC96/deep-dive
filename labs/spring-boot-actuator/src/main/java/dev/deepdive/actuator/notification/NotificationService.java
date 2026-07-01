package dev.deepdive.actuator.notification;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final Counter notificationSentCounter;
    private final Counter emailNotificationCounter;
    private final Counter smsNotificationCounter;

    public NotificationService(MeterRegistry meterRegistry) {
        // tag 없는 counter: 전체 발송 성공 횟수를 볼 때 사용
        this.notificationSentCounter = Counter.builder("app.notification.sent")
                .description("성공적으로 발송된 알림 수")
                .register(meterRegistry);
        // 같은 metric 이름에 channel/region tag를 붙이면 필터링 가능한 시계열이 된다.
        this.emailNotificationCounter = Counter.builder("app.notification.sent")
                .description("성공적으로 발송된 알림 수")
                .tag("channel", "email")
                .tag("region", "kr")
                .register(meterRegistry);
        // channel=sms만 보고 싶으면 /actuator/metrics/app.notification.sent?tag=channel:sms
        this.smsNotificationCounter = Counter.builder("app.notification.sent")
                .description("성공적으로 발송된 알림 수")
                .tag("channel", "sms")
                .tag("region", "kr")
                .register(meterRegistry);
    }

    @Counted(value = "app.notification.requested", description = "알림 발송 요청 횟수")
    public NotificationReceipt send(String channel, String recipient, String message) {
        // @Counted는 메서드 호출 횟수를 AOP로 기록하고, 아래 Counter는 성공 처리 후 직접 증가시킨다.
        NotificationReceipt receipt = new NotificationReceipt(channel, recipient, message, "SENT");
        notificationSentCounter.increment();

        if ("email".equals(channel)) {
            emailNotificationCounter.increment();
        } else if ("sms".equals(channel)) {
            smsNotificationCounter.increment();
        }

        return receipt;
    }
}
