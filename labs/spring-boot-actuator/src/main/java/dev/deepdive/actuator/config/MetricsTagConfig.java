package dev.deepdive.actuator.config;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsTagConfig {

    @Bean
    public MeterFilter hostnameCommonTag() {
        // 설정 기반 common tag 외에 코드로 hostname tag를 모든 metric에 붙인다.
        return MeterFilter.commonTags(List.of(Tag.of("hostname", hostname())));
    }

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return "unknown";
        }
    }
}
