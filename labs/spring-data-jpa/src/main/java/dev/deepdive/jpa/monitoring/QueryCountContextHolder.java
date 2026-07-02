package dev.deepdive.jpa.monitoring;

/**
 * 현재 요청의 {@link RequestContext}를 스레드에 묶어 두는 ThreadLocal 보관소.
 *
 * <p>Hibernate의 {@code StatementInspector}는 Spring 빈이 아니라 SQL을 실행하는
 * 콜 스택 한가운데서 불린다. 거기서 "지금 어떤 API를 처리 중인가"를 알아낼
 * 방법은 메서드 인자로 컨텍스트를 넘겨받는 게 아니라, 같은 스레드에 미리 심어 둔
 * 값을 꺼내는 것뿐이다. 그 다리 역할을 ThreadLocal이 한다.
 *
 * <p>핵심 규약: {@link #set}으로 심었으면 요청이 끝날 때 반드시 {@link #clear}로
 * 지운다. 톰캣은 스레드를 풀에서 재사용하므로, 안 지우면 다음 요청이 이전 요청의
 * 컨텍스트를 물려받는 누수가 생긴다.
 */
public final class QueryCountContextHolder {

    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

    private QueryCountContextHolder() {
    }

    public static void set(RequestContext context) {
        CONTEXT.set(context);
    }

    public static RequestContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
