package dev.deepdive.actuator.endpoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

// @Endpoint(id = "features") -> /actuator/features 로 노출됨
// id는 소문자/숫자만 가능하고, application.yml의 exposure.include에도 같은 id를 넣어야 보임
@Component
@Endpoint(id = "features")
public class FeatureToggleEndpoint {

    // 실제로는 DB나 외부 설정 저장소를 쓰겠지만, 예제라서 메모리에만 둠
    private final Map<String, Boolean> toggles = new ConcurrentHashMap<>();

    // GET /actuator/features -> 전체 토글 상태 반환
    @ReadOperation
    public Map<String, Boolean> features() {
        return toggles;
    }

    // GET /actuator/features/{name} -> @Selector로 path 변수를 받아 단건 조회
    @ReadOperation
    public Map<String, Object> feature(@Selector String name) {
        return Map.of(
                "name", name,
                "enabled", toggles.getOrDefault(name, false));
    }

    // POST /actuator/features/{name}  body: {"enabled": true}
    // @WriteOperation의 파라미터는 요청 body의 key와 매핑됨 (enabled가 없으면 기본 true)
    @WriteOperation
    public Map<String, Object> setFeature(@Selector String name, Boolean enabled) {
        boolean value = enabled == null || enabled;
        toggles.put(name, value);
        return Map.of(
                "name", name,
                "enabled", value);
    }

    // DELETE /actuator/features/{name} -> 토글 제거
    @DeleteOperation
    public Map<String, Object> removeFeature(@Selector String name) {
        Boolean removed = toggles.remove(name);
        return Map.of(
                "name", name,
                "removed", removed != null);
    }
}
