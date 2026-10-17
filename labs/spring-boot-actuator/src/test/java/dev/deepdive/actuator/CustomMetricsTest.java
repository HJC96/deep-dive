package dev.deepdive.actuator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import dev.deepdive.actuator.document.DocumentValidationService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/**
 * README 4, 6~10번 섹션의 custom metric을 App API로 움직인 뒤 actuator 응답으로 확인한다.
 *
 * <p>같은 컨텍스트 안에서는 값이 누적되므로 전부 "호출 전후 차이"로 검증한다.
 */
class CustomMetricsTest extends ActuatorHttpTestSupport {

    private static final String SENT = "app.notification.sent";

    @Autowired
    DocumentValidationService documentValidationService;

    @Test
    void 발송_3건이면_tag없이_조회한_COUNT도_3이다() {
        double total = count(SENT);
        double email = count(SENT, "channel:email");
        double sms = count(SENT, "channel:sms");
        double emailKr = count(SENT, "channel:email", "region:kr");
        double requested = count("app.notification.requested");

        // README 4번 요청 순서 그대로. /notifications/send의 기본 channel은 email이다.
        rest.postForObject("/notifications/send", null, JsonNode.class);
        rest.postForObject("/notifications/email", null, JsonNode.class);
        rest.postForObject("/notifications/sms", null, JsonNode.class);

        // tag 필터 없이 조회하면 actuator는 같은 이름의 시계열을 모두 더한다.
        // tag 없는 counter를 따로 올리던 예전 구조에서는 여기가 6이었다.
        assertThat(count(SENT) - total).isEqualTo(3.0);
        assertThat(count(SENT, "channel:email") - email).isEqualTo(2.0);
        assertThat(count(SENT, "channel:sms") - sms).isEqualTo(1.0);
        assertThat(count(SENT, "channel:email", "region:kr") - emailKr).isEqualTo(2.0);
        // @Counted는 메서드 호출 횟수
        assertThat(count("app.notification.requested") - requested).isEqualTo(3.0);
    }

    @Test
    void 정해지지_않은_channel은_other로_묶여_tag_값이_늘지_않는다() {
        double other = count(SENT, "channel:other");

        // channel 값을 그대로 tag로 쓰면 push, kakao, ... 처럼 요청마다 새 시계열이 생길 수 있다.
        rest.postForObject("/notifications/send?channel=push", null, JsonNode.class);

        assertThat(count(SENT, "channel:other") - other).isEqualTo(1.0);
        assertThat(availableTagValues(SENT, "channel")).containsOnly("email", "sms", "other");
    }

    @Test
    void Gauge는_조회_시점의_대기열_크기를_보여준다() {
        double before = statistic("app.notification.pending.size", "VALUE");

        rest.postForObject("/notifications/pending", null, JsonNode.class);
        rest.postForObject("/notifications/pending?message=retry", null, JsonNode.class);
        assertThat(statistic("app.notification.pending.size", "VALUE") - before).isEqualTo(2.0);

        rest.exchange("/notifications/pending", HttpMethod.DELETE, null, JsonNode.class);
        assertThat(statistic("app.notification.pending.size", "VALUE") - before).isEqualTo(1.0);

        rest.exchange("/notifications/pending", HttpMethod.DELETE, null, JsonNode.class);
    }

    @Test
    void Timer_record와_Timed는_호출_횟수와_소요_시간을_함께_기록한다() {
        double fileCount = count("app.file.conversion.time");
        double fileTotal = statistic("app.file.conversion.time", "TOTAL_TIME");
        double reportCount = count("app.report.generation.time");
        double reportTotal = statistic("app.report.generation.time", "TOTAL_TIME");

        rest.postForObject("/files/convert", null, JsonNode.class);   // 약 2초
        rest.postForObject("/reports/generate", null, JsonNode.class); // 약 0.75초

        assertThat(count("app.file.conversion.time") - fileCount).isEqualTo(1.0);
        assertThat(statistic("app.file.conversion.time", "TOTAL_TIME") - fileTotal).isGreaterThanOrEqualTo(2.0);
        assertThat(count("app.report.generation.time") - reportCount).isEqualTo(1.0);
        assertThat(statistic("app.report.generation.time", "TOTAL_TIME") - reportTotal).isGreaterThanOrEqualTo(0.75);
    }

    @Test
    void Timer_Sample의_result_tag는_실제_결과로_정해진다() {
        String timer = "app.document.validation.time";
        double success = count(timer, "result:success");
        double fail = count(timer, "result:fail");
        double error = count(timer, "result:error");

        rest.postForObject("/documents/validate?valid=true", null, JsonNode.class);
        rest.postForObject("/documents/validate?valid=false", null, JsonNode.class);
        // 검증 도중 예외가 나면 결과를 알 수 없으니 error로 남는다. 예외로 끝난 호출도 시간은 기록된다.
        assertThatThrownBy(() -> documentValidationService.validate(" ", true))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(count(timer, "result:success") - success).isEqualTo(1.0);
        assertThat(count(timer, "result:fail") - fail).isEqualTo(1.0);
        assertThat(count(timer, "result:error") - error).isEqualTo(1.0);
        assertThat(availableTagValues(timer, "result")).contains("success", "fail", "error");
    }

    @Test
    void percentile은_phi_tag로_하나씩_조회해야_의미가_있다() {
        String percentile = "app.content.search.time.percentile";

        // README 10번 요청 순서. keyword 길이 % 3으로 지연이 정해진다.
        rest.getForObject("/contents/search", JsonNode.class);                         // actuator(8) → 900ms
        rest.getForObject("/contents/search?keyword=observability", JsonNode.class);   // observability(13) → 450ms

        double p50 = statistic(percentile, "VALUE", "phi:0.5");
        double p95 = statistic(percentile, "VALUE", "phi:0.95");
        double p99 = statistic(percentile, "VALUE", "phi:0.99");
        double unfiltered = statistic(percentile, "VALUE");

        // 표본이 두 개뿐이라 p50은 450ms 쪽, p95/p99는 900ms 쪽이다.
        // 값은 히스토그램 버킷 근삿값이라 sleep 시간과 딱 맞지 않고 위아래로 조금 어긋난다(실측 0.436, 0.503, 0.923 등).
        // 그래서 "어느 쪽 표본인가"만 구분할 수 있는 범위로 검증한다.
        assertThat(p50).isBetween(0.4, 0.7);
        assertThat(p95).isGreaterThan(0.8);
        assertThat(p99).isGreaterThan(0.8);
        // phi 필터 없이 조회하면 세 percentile gauge의 VALUE를 그냥 더한 값이 나온다. 지연 시간으로 읽으면 안 된다.
        assertThat(unfiltered).isCloseTo(p50 + p95 + p99, within(1e-6));
    }

    private List<String> availableTagValues(String metricName, String tag) {
        List<String> values = new ArrayList<>();
        for (JsonNode availableTag : getActuator("/metrics/" + metricName).getBody().get("availableTags")) {
            if (tag.equals(availableTag.get("tag").asText())) {
                availableTag.get("values").forEach(value -> values.add(value.asText()));
            }
        }
        return values;
    }
}
