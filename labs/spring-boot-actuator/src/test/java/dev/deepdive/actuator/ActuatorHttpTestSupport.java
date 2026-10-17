package dev.deepdive.actuator;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 앱 포트와 actuator 포트를 실제로 띄워 HTTP로 확인한다.
 *
 * <p>application.yml은 actuator를 9090에 따로 두므로 테스트도 같은 구조를 유지하되, 포트 충돌을 피하려고
 * 두 포트 모두 랜덤으로 바꾼다. App API는 {@link #rest}의 기본 주소(앱 포트)로, actuator는
 * {@link #actuator(String)}(management 포트)로 부른다.
 *
 * <p>MeterRegistry는 Spring 컨텍스트가 캐시되는 동안 계속 누적된다. 그래서 metric 검증은 절댓값이 아니라
 * "호출 전후 차이"로 한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
abstract class ActuatorHttpTestSupport {

    @Autowired
    TestRestTemplate rest;

    @LocalManagementPort
    int managementPort;

    String actuator(String path) {
        return "http://localhost:" + managementPort + "/actuator" + path;
    }

    ResponseEntity<JsonNode> getActuator(String path) {
        return rest.getForEntity(actuator(path), JsonNode.class);
    }

    /**
     * {@code /actuator/metrics/{name}?tag=...}의 statistic 값을 읽는다.
     *
     * <p>아직 한 번도 기록되지 않은 tag 조합은 actuator가 404를 돌려주므로 0으로 본다.
     */
    double statistic(String metricName, String statistic, String... tags) {
        String query = tags.length == 0 ? "" : "?" + Arrays.stream(tags)
                .map(tag -> "tag=" + tag)
                .collect(Collectors.joining("&"));
        ResponseEntity<JsonNode> response = getActuator("/metrics/" + metricName + query);
        if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
            return 0.0;
        }
        for (JsonNode measurement : response.getBody().get("measurements")) {
            if (statistic.equals(measurement.get("statistic").asText())) {
                return measurement.get("value").asDouble();
            }
        }
        throw new IllegalStateException(metricName + "에 " + statistic + " statistic이 없다: " + response.getBody());
    }

    double count(String metricName, String... tags) {
        return statistic(metricName, "COUNT", tags);
    }
}
