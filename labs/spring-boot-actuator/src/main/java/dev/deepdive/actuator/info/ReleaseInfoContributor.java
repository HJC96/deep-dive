package dev.deepdive.actuator.info;

import java.time.LocalDate;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ReleaseInfoContributor implements InfoContributor {

    private final Environment environment;

    public ReleaseInfoContributor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void contribute(Info.Builder builder) {
        // /actuator/info response에 release 블록을 추가하는 custom contributor
        builder.withDetail("release", new ReleaseInfo(
                "backend-platform",
                LocalDate.of(2026, 6, 29).toString(),
                "#actuator-lab",
                activeProfiles()
        ));
    }

    private String activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            // active profile이 없으면 actuator response에서 default로 읽기 쉽게 표시
            return "default";
        }
        return String.join(",", profiles);
    }

    public record ReleaseInfo(
            String releaseManager,
            String releasedAt,
            String supportChannel,
            String activeProfile
    ) {
    }
}
