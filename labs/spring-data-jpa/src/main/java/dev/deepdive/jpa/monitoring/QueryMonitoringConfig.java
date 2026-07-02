package dev.deepdive.jpa.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 쿼리 모니터링의 세 조각을 스프링에 등록하는 설정.
 *
 * <ol>
 *   <li><b>인터셉터</b>를 MVC 파이프라인에 끼워 요청 앞뒤로 ThreadLocal을 깔고 치운다.</li>
 *   <li><b>StatementInspector</b>를 Hibernate 프로퍼티에 instance로 꽂는다. 클래스 이름
 *       문자열 대신 인스턴스를 넘기면 Hibernate가 직접 new 하지 않고 이 객체를 그대로 쓴다.</li>
 * </ol>
 *
 * <p>인스펙터는 메트릭을 기록하지 않고 ThreadLocal만 만지므로 Spring 빈일 필요가 없다.
 * 실제 {@link MeterRegistry} 기록은 인터셉터(요청 스레드의 끝)에서 한 번에 일어난다.
 */
@Configuration
public class QueryMonitoringConfig implements WebMvcConfigurer {

    private final MeterRegistry meterRegistry;

    public QueryMonitoringConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new QueryCountInterceptor(meterRegistry));
    }

    @Bean
    HibernatePropertiesCustomizer queryCountStatementInspectorCustomizer() {
        QueryCountStatementInspector inspector = new QueryCountStatementInspector();
        return properties -> properties.put(AvailableSettings.STATEMENT_INSPECTOR, inspector);
    }
}
