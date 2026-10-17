package dev.deepdive.paymentsystem.ledger.domain;

import java.util.List;
import java.util.Map;

/**
 * Payment 가 보낸 결제 승인 이벤트. payload 에 주문 라인(paymentOrders)과 총액이 실려 온다(Event-Carried State Transfer).
 * 장부에 적을 금액을 payment DB 나 자기 DB 의 복제 테이블을 읽지 않고 메시지에서 바로 얻는다.
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
     * payload 의 주문 라인을 도메인 객체로 바꾼다. 주문 라인이 없는 예전 형식(payload v1)은 예외를 던져
     * 재시도 후 DLQ 로 격리되게 한다.
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
        return lines.stream()
                .map(line -> {
                    if (!(line instanceof Map<?, ?> fields)) {
                        throw new IllegalArgumentException("주문 라인 형식이 올바르지 않습니다: " + line);
                    }
                    return new PaymentOrder(
                            requireLong(fields, "paymentOrderId", orderId),
                            requireLong(fields, "amount", orderId),
                            orderId
                    );
                })
                .toList();
    }

    /**
     * 결제 총액. 장부 대변 합계가 이 값과 같아야 한다. 없으면 주문 라인 합을 쓴다.
     */
    public long totalAmount() {
        if (payload.get("totalAmount") instanceof Number totalAmount) {
            return totalAmount.longValue();
        }
        return paymentOrders().stream().mapToLong(PaymentOrder::amount).sum();
    }

    private static long requireLong(Map<?, ?> fields, String name, String orderId) {
        if (!(fields.get(name) instanceof Number value)) {
            throw new IllegalArgumentException("결제 이벤트 (orderId: " + orderId + ") 주문 라인에 " + name + " 가 없습니다.");
        }
        return value.longValue();
    }
}
