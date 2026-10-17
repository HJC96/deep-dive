package dev.deepdive.jpa.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 인터셉터가 어떤 uri tag로 메트릭을 남기는지 확인한다. Spring 컨텍스트 없이 인터셉터만 직접 부른다.
 */
class QueryCountInterceptorTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final QueryCountInterceptor interceptor = new QueryCountInterceptor(registry);

    @Test
    void 매칭된_패턴이_있으면_원시_경로_대신_패턴으로_태깅한다() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products/42");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/products/{id}");

        handle(request, 1);

        DistributionSummary summary = registry.get("jpa.queries.per_request").summary();
        assertThat(summary.getId().getTag("uri")).isEqualTo("/products/{id}");
        assertThat(summary.totalAmount()).isEqualTo(1.0);
    }

    @Test
    void 패턴이_없으면_원시_URI가_아니라_고정값_UNKNOWN으로_묶는다() {
        // 패턴 없는 요청의 경로를 그대로 tag로 쓰면 경로마다 시계열이 하나씩 생긴다.
        handle(new MockHttpServletRequest("GET", "/no-pattern/1"), 1);
        handle(new MockHttpServletRequest("GET", "/no-pattern/2"), 2);

        assertThat(registry.get("jpa.queries.per_request").meters())
                .extracting(meter -> meter.getId().getTag("uri"))
                .containsExactly(QueryCountInterceptor.UNKNOWN_URI);
        assertThat(registry.get("jpa.queries.by_type").meters())
                .extracting(Meter::getId)
                .allSatisfy(id -> assertThat(id.getTag("uri")).isEqualTo(QueryCountInterceptor.UNKNOWN_URI));

        DistributionSummary summary = registry.get("jpa.queries.per_request").summary();
        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.totalAmount()).isEqualTo(3.0);
    }

    @Test
    void 요청이_끝나면_ThreadLocal을_비운다() {
        handle(new MockHttpServletRequest("GET", "/products"), 1);

        assertThat(QueryCountContextHolder.get()).isNull();
    }

    /** preHandle → (SQL 실행 흉내) → afterCompletion 순서로 요청 하나를 처리한다. */
    private void handle(MockHttpServletRequest request, int selectCount) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());
        QueryCountStatementInspector inspector = new QueryCountStatementInspector();
        for (int i = 0; i < selectCount; i++) {
            inspector.inspect("select * from product");
        }
        interceptor.afterCompletion(request, response, new Object(), null);
    }
}
