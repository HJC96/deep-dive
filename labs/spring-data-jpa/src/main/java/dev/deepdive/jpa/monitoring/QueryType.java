package dev.deepdive.jpa.monitoring;

/**
 * Hibernate가 실행하려는 SQL의 첫 키워드로 분류한 쿼리 종류.
 *
 * <p>{@link QueryCountStatementInspector}가 SQL 문자열을 받아 이 enum으로 분류하고,
 * {@link RequestContext}는 종류별로 카운트를 따로 쌓는다. 그래야 "이 API가 SELECT를
 * 몇 번, INSERT를 몇 번 날렸나"를 지표로 쪼개 볼 수 있다.
 */
public enum QueryType {
    SELECT,
    INSERT,
    UPDATE,
    DELETE,
    OTHER;

    /**
     * SQL 문자열의 선행 공백을 제거하고 첫 키워드만 보고 분류한다.
     *
     * <p>Hibernate는 기본적으로 주석 없이 {@code select ...} 형태로 SQL을 넘기므로
     * 첫 토큰만 검사하면 충분하다. ({@code use_sql_comments}를 켜면 앞에 주석이 붙지만
     * 이 lab에서는 끄고 쓴다.)
     */
    static QueryType from(String sql) {
        if (sql == null) {
            return OTHER;
        }
        String trimmed = sql.stripLeading();
        if (startsWithIgnoreCase(trimmed, "select")) {
            return SELECT;
        }
        if (startsWithIgnoreCase(trimmed, "insert")) {
            return INSERT;
        }
        if (startsWithIgnoreCase(trimmed, "update")) {
            return UPDATE;
        }
        if (startsWithIgnoreCase(trimmed, "delete")) {
            return DELETE;
        }
        return OTHER;
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
