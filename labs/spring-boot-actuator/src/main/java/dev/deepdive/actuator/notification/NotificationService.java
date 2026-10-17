package dev.deepdive.actuator.notification;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final String SENT_METRIC = "app.notification.sent";

    private final Counter emailNotificationCounter;
    private final Counter smsNotificationCounter;
    private final Counter otherNotificationCounter;

    public NotificationService(MeterRegistry meterRegistry) {
        // 같은 metric 이름의 시계열은 모두 channel/region tag를 가진다. tag 없는 "전체" counter는 따로 두지 않는다.
        // /actuator/metrics/app.notification.sent 를 tag 없이 조회하면 같은 이름의 시계열을 전부 더해서 보여 주므로,
        // tag 없는 counter를 함께 올리면 한 번 발송이 두 번 세진다(3건 발송 → COUNT 6).
        // 전체 발송 수는 tag 필터 없이 조회한 합계가 곧 전체다.
        this.emailNotificationCounter = sentCounter(meterRegistry, "email");
        // channel=sms만 보고 싶으면 /actuator/metrics/app.notification.sent?tag=channel:sms
        this.smsNotificationCounter = sentCounter(meterRegistry, "sms");
        // 요청 파라미터 channel 값을 그대로 tag로 쓰면 값 종류가 끝없이 늘 수 있어(cardinality) 나머지는 other로 묶는다.
        this.otherNotificationCounter = sentCounter(meterRegistry, "other");
    }

    private static Counter sentCounter(MeterRegistry meterRegistry, String channel) {
        return Counter.builder(SENT_METRIC)
                .description("성공적으로 발송된 알림 수")
                .tag("channel", channel)
                .tag("region", "kr")
                .register(meterRegistry);
    }

    @Counted(value = "app.notification.requested", description = "알림 발송 요청 횟수")
    public NotificationReceipt send(String channel, String recipient, String message) {
        // @Counted는 메서드 호출 횟수를 AOP로 기록하고, 아래 Counter는 성공 처리 후 직접 증가시킨다.
        NotificationReceipt receipt = new NotificationReceipt(channel, recipient, message, "SENT");
        counterFor(channel).increment();
        return receipt;
    }

    private Counter counterFor(String channel) {
        return switch (channel) {
            case "email" -> emailNotificationCounter;
            case "sms" -> smsNotificationCounter;
            default -> otherNotificationCounter;
        };
    }
}
