package dev.deepdive.jpa.monitoring;

import java.util.EnumMap;
import java.util.Map;

/**
 * 한 번의 HTTP 요청 동안 누적되는 쿼리 카운트 묶음.
 *
 * <p>인터셉터가 요청 시작 시점에 (HTTP 메서드, URI 패턴)으로 하나 만들어
 * {@link QueryCountContextHolder}의 ThreadLocal에 넣어 둔다. 같은 스레드에서
 * 도는 컨트롤러·서비스가 쿼리를 날릴 때마다 {@link QueryCountStatementInspector}가
 * 이 객체의 카운터를 올린다.
 *
 * <p>하나의 요청은 하나의 스레드 안에서만 이 객체를 만지므로(ThreadLocal) 별도
 * 동기화는 두지 않는다. 비동기로 작업을 다른 스레드에 넘기면 그 스레드에는
 * 컨텍스트가 없어 카운트되지 않는다 — 이 lab의 의도된 한계다.
 */
public final class RequestContext {

    private final String httpMethod;
    private final String uriPattern;
    private final Map<QueryType, Integer> counts = new EnumMap<>(QueryType.class);

    public RequestContext(String httpMethod, String uriPattern) {
        this.httpMethod = httpMethod;
        this.uriPattern = uriPattern;
    }

    void increment(QueryType type) {
        counts.merge(type, 1, Integer::sum);
    }

    public String httpMethod() {
        return httpMethod;
    }

    public String uriPattern() {
        return uriPattern;
    }

    public int countOf(QueryType type) {
        return counts.getOrDefault(type, 0);
    }

    public int totalCount() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
