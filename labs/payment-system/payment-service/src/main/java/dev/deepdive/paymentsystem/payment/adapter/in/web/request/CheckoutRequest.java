package dev.deepdive.paymentsystem.payment.adapter.in.web.request;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 체크아웃 화면 요청. 쿼리 파라미터가 없으면 원본처럼 기본값으로 채운다.
 * seed 는 idempotencyKey 생성에 쓰여, 같은 장바구니라도 요청마다 다른 주문이 되게 한다.
 */
public record CheckoutRequest(
        Long cartId,
        List<Long> productIds,
        Long buyerId,
        String seed
) {

    public CheckoutRequest {
        if (cartId == null) {
            cartId = 1L;
        }
        if (productIds == null || productIds.isEmpty()) {
            productIds = List.of(1L, 2L, 3L);
        }
        if (buyerId == null) {
            buyerId = 1L;
        }
        if (seed == null) {
            seed = LocalDateTime.now().toString();
        }
    }
}
