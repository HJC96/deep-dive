package dev.deepdive.paymentsystem.ledger.test;

import dev.deepdive.paymentsystem.ledger.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.ledger.domain.PaymentEventMessageType;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * payment-service 가 아웃박스에 쓰는 것과 같은 모양(payload v2)의 결제 승인 이벤트를 만든다.
 */
public final class PaymentEventMessages {

    private PaymentEventMessages() {
    }

    public record Line(long paymentOrderId, long sellerId, long amount) {
    }

    public static PaymentEventMessage paymentConfirmed(String orderId, Line... lines) {
        return paymentConfirmed(orderId, Arrays.stream(lines).mapToLong(Line::amount).sum(), lines);
    }

    public static PaymentEventMessage paymentConfirmed(String orderId, long totalAmount, Line... lines) {
        List<Map<String, Object>> paymentOrders = Arrays.stream(lines)
                .<Map<String, Object>>map(line -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("paymentOrderId", line.paymentOrderId());
                    map.put("sellerId", line.sellerId());
                    map.put("productId", line.paymentOrderId());
                    map.put("amount", line.amount());
                    return map;
                })
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("paymentKey", "pk_" + orderId);
        payload.put("totalAmount", totalAmount);
        payload.put("paymentOrders", paymentOrders);
        return new PaymentEventMessage(
                PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS,
                payload,
                Map.of("partitionKey", 0, "payloadVersion", 2)
        );
    }
}
