package dev.deepdive.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * README 0~2, 12번 섹션(health, info, custom endpoint)의 대표 response를 실제 HTTP 응답으로 확인한다.
 */
class ActuatorEndpointTest extends ActuatorHttpTestSupport {

    @Test
    void 외부시스템이_DOWN이면_health가_DOWN과_503을_돌려준다() {
        try {
            ResponseEntity<JsonNode> up = getActuator("/health/partnerSystem");
            assertThat(up.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(up.getBody().get("status").asText()).isEqualTo("UP");

            // App API로 외부 시스템 상태를 내린다. HealthIndicator는 조회 시점에 이 값을 읽는다.
            rest.postForObject("/partners/system/down", null, JsonNode.class);

            ResponseEntity<JsonNode> down = getActuator("/health/partnerSystem");
            assertThat(down.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(down.getBody().get("status").asText()).isEqualTo("DOWN");
            assertThat(down.getBody().at("/details/message").asText()).isEqualTo("Partner system is unavailable");

            // component 하나가 DOWN이면 전체 health도 DOWN으로 집계되고 503이 된다.
            ResponseEntity<JsonNode> overall = getActuator("/health");
            assertThat(overall.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(overall.getBody().get("status").asText()).isEqualTo("DOWN");
        } finally {
            // 같은 컨텍스트를 쓰는 다른 테스트에 DOWN 상태가 새지 않도록 되돌린다.
            rest.postForObject("/partners/system/up", null, JsonNode.class);
        }

        assertThat(getActuator("/health/partnerSystem").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void info에는_설정값과_build_정보와_release_블록이_함께_나온다() {
        JsonNode info = getActuator("/info").getBody();

        // management.info.env.enabled=true → info.* 설정
        assertThat(info.at("/app/name").asText()).isEqualTo("spring-boot-actuator");
        assertThat(info.at("/app/version").asText()).isEqualTo("0.1.0-SNAPSHOT");
        assertThat(info.get("team").asText()).isEqualTo("backend-lab");
        // build.gradle의 generateBuildInfo가 만든 META-INF/build-info.properties
        assertThat(info.at("/build/artifact").asText()).isEqualTo("spring-boot-actuator");
        // ReleaseInfoContributor
        assertThat(info.at("/release/releaseManager").asText()).isEqualTo("backend-platform");
        assertThat(info.at("/release/releasedAt").asText()).isEqualTo("2026-06-29");
        assertThat(info.at("/release/supportChannel").asText()).isEqualTo("#actuator-lab");
        assertThat(info.at("/release/activeProfile").asText()).isEqualTo("default");
    }

    @Test
    void features_endpoint로_토글을_쓰고_읽고_지운다() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> body = new HttpEntity<>(Map.of("enabled", true), headers);

        // @WriteOperation: POST body의 enabled 키가 메서드 파라미터로 들어간다.
        JsonNode written = rest.postForObject(actuator("/features/dark-mode"), body, JsonNode.class);
        assertThat(written.get("name").asText()).isEqualTo("dark-mode");
        assertThat(written.get("enabled").asBoolean()).isTrue();

        // @ReadOperation (selector 없음): 전체 맵
        assertThat(getActuator("/features").getBody().get("dark-mode").asBoolean()).isTrue();
        // @ReadOperation + @Selector: 단건
        assertThat(getActuator("/features/dark-mode").getBody().get("enabled").asBoolean()).isTrue();

        // @DeleteOperation
        JsonNode removed = rest.exchange(actuator("/features/dark-mode"), HttpMethod.DELETE, null, JsonNode.class)
                .getBody();
        assertThat(removed.get("removed").asBoolean()).isTrue();

        // 지운 뒤에는 단건 조회가 기본값 false를 돌려주고, 전체 맵에서도 빠진다.
        assertThat(getActuator("/features/dark-mode").getBody().get("enabled").asBoolean()).isFalse();
        assertThat(getActuator("/features").getBody().has("dark-mode")).isFalse();
    }
}
