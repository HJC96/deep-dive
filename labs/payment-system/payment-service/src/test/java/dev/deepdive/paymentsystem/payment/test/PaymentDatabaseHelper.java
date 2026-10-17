package dev.deepdive.paymentsystem.payment.test;

import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface PaymentDatabaseHelper {

    PaymentEvent getPayments(String orderId);

    /**
     * 아웃박스 행(status, payload, metadata). 없으면 null.
     */
    Map<String, Object> getOutbox(String orderId);

    List<Integer> getFailedCounts(String orderId);

    List<String> getHistoryReasons(String orderId);

    /**
     * 주문 행의 updated_at 을 과거로 돌린다. "3분 넘게 EXECUTING" 같은 시간 조건을 기다리지 않고 만든다.
     */
    void setPaymentOrderUpdatedAtMinutesAgo(String orderId, int minutes);

    void setOutboxCreatedAtMinutesAgo(String orderId, int minutes);

    Mono<Void> clean();
}
