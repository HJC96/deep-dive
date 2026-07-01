package dev.deepdive.actuator.partner;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/partners/system")
public class PartnerSystemController {

    private final PartnerSystemSimulator partnerSystem;

    public PartnerSystemController(PartnerSystemSimulator partnerSystem) {
        this.partnerSystem = partnerSystem;
    }

    @GetMapping
    public Map<String, Object> status() {
        return response();
    }

    @PostMapping("/up")
    public Map<String, Object> up() {
        // App API로 상태를 바꾸면 actuator health response도 같은 상태를 읽는다.
        partnerSystem.markAvailable();
        return response();
    }

    @PostMapping("/down")
    public Map<String, Object> down() {
        // DOWN 상태에서는 /actuator/health/partnerSystem 응답 status가 DOWN이 된다.
        partnerSystem.markUnavailable();
        return response();
    }

    private Map<String, Object> response() {
        return Map.of(
                "available", partnerSystem.isAvailable(),
                "changedAt", partnerSystem.changedAt()
        );
    }
}
