package dev.deepdive.paymentsystem.wallet.domain;

import java.util.List;
import java.util.Map;

/**
 * Payment 가 보낸 결제 승인 이벤트. payload 에 주문 라인(paymentOrders)이 실려 온다(Event-Carried State Transfer).
 * 정산에 필요한 판매자·금액을 payment DB 나 자기 DB 의 복제 테이블을 읽지 않고 메시지에서 바로 얻는다.
 */
public record PaymentEventMessage(
        PaymentEventMessageType type,
        Map<String, Object> payload,
        Map<String, Object> metadata
) {

    public PaymentEventMessage {
        if (payload == null) {
            payload = Map.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }

    public String orderId() {
        return (String) payload.get("orderId");
    }

    /**
     * payload 의 주문 라인을 도메인 객체로 바꾼다. 주문 라인이 없는 예전 형식(orderId 만 있는 payload v1)이거나
     * 금액 합이 totalAmount 와 다르면 예외를 던진다. 처리하지 못한 메시지는 재시도 후 DLQ 로 격리된다.
     */
    public List<PaymentOrder> paymentOrders() {
        String orderId = orderId();
        if (orderId == null) {
            throw new IllegalArgumentException("결제 이벤트에 orderId 가 없습니다. payload: " + payload);
        }
        if (!(payload.get("paymentOrders") instanceof List<?> lines) || lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "결제 이벤트 (orderId: " + orderId + ") 에 주문 라인(paymentOrders)이 없습니다. 예전 형식(payload v1) 메시지입니다.");
        }

        List<PaymentOrder> paymentOrders = lines.stream()
                .map(line -> {
                    if (!(line instanceof Map<?, ?> fields)) {
                        throw new IllegalArgumentException("주문 라인 형식이 올바르지 않습니다: " + line);
                    }
                    return new PaymentOrder(
                            requireLong(fields, "paymentOrderId", orderId),
                            requireLong(fields, "sellerId", orderId),
                            requireLong(fields, "amount", orderId),
                            orderId
                    );
                })
                .toList();

        if (payload.get("totalAmount") instanceof Number totalAmount) {
            long sum = paymentOrders.stream().mapToLong(PaymentOrder::amount).sum();
            if (sum != totalAmount.longValue()) {
                throw new IllegalArgumentException(
                        "결제 이벤트 (orderId: " + orderId + ") 의 주문 금액 합(" + sum + ")이 totalAmount(" + totalAmount + ")와 다릅니다.");
            }
        }
        return paymentOrders;
    }

    private static long requireLong(Map<?, ?> fields, String name, String orderId) {
        if (!(fields.get(name) instanceof Number value)) {
            throw new IllegalArgumentException("결제 이벤트 (orderId: " + orderId + ") 주문 라인에 " + name + " 가 없습니다.");
        }
        return value.longValue();
    }
}
