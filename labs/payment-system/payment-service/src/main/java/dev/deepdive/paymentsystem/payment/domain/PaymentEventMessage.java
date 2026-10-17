package dev.deepdive.paymentsystem.payment.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PaymentEventMessage(
        PaymentEventMessageType type,
        Map<String, Object> payload,
        Map<String, Object> metadata
) {

    /**
     * payload 형식 버전. 1 = orderId 만 담던 형식, 2 = 주문 라인(paymentOrders)까지 담는 형식(Event-Carried State Transfer).
     */
    public static final int PAYLOAD_VERSION = 2;

    public PaymentEventMessage {
        if (payload == null) {
            payload = Map.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }

    /**
     * 결제 승인 성공 이벤트. Wallet·Ledger 가 payment DB 를 읽지 않고도 정산·장부 기록을 할 수 있게
     * 주문 라인(paymentOrderId, sellerId, productId, amount)과 총액을 실어 보낸다.
     */
    public static PaymentEventMessage paymentConfirmationSuccess(
            String orderId,
            String paymentKey,
            List<PaymentOrder> paymentOrders,
            int partitionKey
    ) {
        if (paymentOrders == null || paymentOrders.isEmpty()) {
            throw new IllegalArgumentException("결제 (orderId: " + orderId + ") 의 주문 라인이 비어 있습니다.");
        }

        List<Map<String, Object>> lines = paymentOrders.stream()
                .<Map<String, Object>>map(order -> {
                    Map<String, Object> line = new LinkedHashMap<>();
                    line.put("paymentOrderId", order.id());
                    line.put("sellerId", order.sellerId());
                    line.put("productId", order.productId());
                    line.put("amount", order.amount());
                    return line;
                })
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("paymentKey", paymentKey);
        payload.put("totalAmount", paymentOrders.stream().mapToLong(PaymentOrder::amount).sum());
        payload.put("paymentOrders", lines);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("partitionKey", partitionKey);
        metadata.put("payloadVersion", PAYLOAD_VERSION);

        return new PaymentEventMessage(PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS, payload, metadata);
    }

    public String orderId() {
        return (String) payload.get("orderId");
    }
}
