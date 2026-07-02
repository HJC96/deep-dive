package dev.deepdive.jpa.monitoring;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Hibernate가 SQL을 JDBC로 넘기기 직전에 매번 거치는 가로채기 지점.
 *
 * <p>{@link #inspect(String)}는 실행하려는 SQL 문자열을 받아 (원하면 고쳐서) 돌려준다.
 * 여기서는 SQL을 건드리지 않고, 현재 스레드의 {@link RequestContext}를 찾아 쿼리
 * 종류별 카운트만 올린 뒤 원본 SQL을 그대로 반환한다.
 *
 * <p>요청 컨텍스트가 없으면(예: 부팅 시 스키마 생성, 배치 스레드) 조용히 넘어간다.
 * 이 객체는 EntityManagerFactory 하나당 하나만 만들어져 모든 스레드가 공유하므로
 * 자체 상태를 두지 않는다. 요청별 상태는 전부 ThreadLocal 쪽에 있다.
 */
public class QueryCountStatementInspector implements StatementInspector {

    @Override
    public String inspect(String sql) {
        RequestContext context = QueryCountContextHolder.get();
        if (context != null) {
            context.increment(QueryType.from(sql));
        }
        return sql;
    }
}
