package dev.deepdive.paymentsystem.wallet.domain;

import dev.deepdive.paymentsystem.wallet.test.PaymentEventMessages;
import dev.deepdive.paymentsystem.wallet.test.PaymentEventMessages.Line;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentEventMessageTest {

    @Test
    void should_read_order_lines_from_the_payload() {
        PaymentEventMessage message = PaymentEventMessages.paymentConfirmed("order-1",
                new Line(1, 10, 1000), new Line(2, 20, 2000));

        List<PaymentOrder> paymentOrders = message.paymentOrders();

        assertThat(paymentOrders).extracting(PaymentOrder::id).containsExactly(1L, 2L);
        assertThat(paymentOrders).extracting(PaymentOrder::sellerId).containsExactly(10L, 20L);
        assertThat(paymentOrders).extracting(PaymentOrder::amount).containsExactly(1000L, 2000L);
        assertThat(paymentOrders).extracting(PaymentOrder::orderId).containsOnly("order-1");
    }

    @Test
    void should_reject_legacy_payload_without_order_lines() {
        PaymentEventMessage legacy = new PaymentEventMessage(
                PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS, Map.of("orderId", "order-1"), Map.of());

        assertThatThrownBy(legacy::paymentOrders).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_order_lines_that_do_not_add_up_to_the_total() {
        PaymentEventMessage message = PaymentEventMessages.paymentConfirmed("order-1", new Line(1, 10, 1000));
        Map<String, Object> payload = new HashMap<>(message.payload());
        payload.put("totalAmount", 999);
        PaymentEventMessage tampered = new PaymentEventMessage(message.type(), payload, message.metadata());

        assertThatThrownBy(tampered::paymentOrders)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalAmount");
    }

    @Test
    void should_reject_an_order_line_without_seller() {
        PaymentEventMessage message = new PaymentEventMessage(
                PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS,
                Map.of("orderId", "order-1", "paymentOrders", List.of(Map.of("paymentOrderId", 1, "amount", 1000))),
                Map.of());

        assertThatThrownBy(message::paymentOrders)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sellerId");
    }
}
