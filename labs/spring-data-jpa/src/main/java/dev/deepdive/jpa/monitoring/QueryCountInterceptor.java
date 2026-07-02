package dev.deepdive.jpa.monitoring;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 요청을 앞뒤로 감싸 쿼리 카운트의 수명주기를 관리하는 인터셉터.
 *
 * <ul>
 *   <li>{@link #preHandle}: HTTP 메서드와 URI <em>패턴</em>을 뽑아 {@link RequestContext}를
 *       만들고 ThreadLocal에 심는다. 컨트롤러·서비스가 돌기 전에 자리부터 깔아 두는 것.</li>
 *   <li>{@link #afterCompletion}: 그 사이 쌓인 카운트를 꺼내 Micrometer에 기록하고,
 *       성공·예외와 무관하게 ThreadLocal을 반드시 지운다(finally).</li>
 * </ul>
 *
 * <p>URI를 원시 경로({@code /products/42}) 대신 패턴({@code /products/{id}})으로 태깅하는
 * 게 중요하다. 원시 경로로 태그를 달면 id 하나하나가 별도 시계열이 되어 메트릭
 * 카디널리티가 폭발한다.
 */
public class QueryCountInterceptor implements HandlerInterceptor {

    /** 요청당 쿼리 수 분포. 여기에 P50·P95 같은 백분위가 붙는다. */
    private static final String QUERIES_PER_REQUEST = "jpa.queries.per_request";

    private final MeterRegistry meterRegistry;

    public QueryCountInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uriPattern = resolveUriPattern(request);
        QueryCountContextHolder.set(new RequestContext(request.getMethod(), uriPattern));
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            RequestContext context = QueryCountContextHolder.get();
            if (context != null) {
                record(context);
            }
        } finally {
            QueryCountContextHolder.clear();
        }
    }

    private void record(RequestContext context) {
        DistributionSummary.builder(QUERIES_PER_REQUEST)
                .description("Number of SQL statements executed per HTTP request")
                .baseUnit("queries")
                .tag("method", context.httpMethod())
                .tag("uri", context.uriPattern())
                .publishPercentiles(0.5, 0.95)
                .register(meterRegistry)
                .record(context.totalCount());

        // 종류별 분포도 남겨 두면 "이 API의 SELECT가 유독 많다" 같은 분석이 된다.
        for (QueryType type : QueryType.values()) {
            int count = context.countOf(type);
            if (count > 0) {
                meterRegistry.counter(
                                "jpa.queries.by_type",
                                "method", context.httpMethod(),
                                "uri", context.uriPattern(),
                                "type", type.name().toLowerCase())
                        .increment(count);
            }
        }
    }

    /**
     * 핸들러 매핑이 골라 둔 best-matching 패턴을 꺼낸다. 정적 리소스 등 패턴이 없는
     * 요청은 원시 URI로 폴백한다.
     */
    private String resolveUriPattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String s && !s.isBlank()) {
            return s;
        }
        return request.getRequestURI();
    }
}
