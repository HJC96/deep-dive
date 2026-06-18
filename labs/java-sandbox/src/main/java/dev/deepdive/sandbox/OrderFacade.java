package dev.deepdive.sandbox;

import java.util.List;

class OrderFacade {

    private final StockService stockService;
    private final PaymentGateway paymentGateway;
    private final DeliveryService deliveryService;
    private final NotificationService notificationService;

    OrderFacade(
            StockService stockService,
            PaymentGateway paymentGateway,
            DeliveryService deliveryService,
            NotificationService notificationService
    ) {
        this.stockService = stockService;
        this.paymentGateway = paymentGateway;
        this.deliveryService = deliveryService;
        this.notificationService = notificationService;
    }

    OrderResult placeOrder(OrderCommand command) {
        Reservation reservation = stockService.reserve(command.productId(), command.quantity());
        Payment payment = paymentGateway.pay(command.memberId(), command.amount());
        Delivery delivery = deliveryService.request(command.memberId(), reservation.id());

        notificationService.send(command.memberId(), delivery.id());

        return new OrderResult(payment.id(), delivery.id());
    }

    record OrderCommand(
            Long memberId,
            Long productId,
            int quantity,
            int amount
    ) {
    }

    record OrderResult(String paymentId, String deliveryId) {
    }

    private record Reservation(String id) {
    }

    private record Payment(String id) {
    }

    private record Delivery(String id) {
    }

    static class StockService {

        private final List<String> events;

        StockService(List<String> events) {
            this.events = events;
        }

        Reservation reserve(Long productId, int quantity) {
            events.add("stock reserved: productId=" + productId + ", quantity=" + quantity);
            return new Reservation("reservation-" + productId);
        }
    }

    static class PaymentGateway {

        private final List<String> events;

        PaymentGateway(List<String> events) {
            this.events = events;
        }

        Payment pay(Long memberId, int amount) {
            events.add("payment approved: memberId=" + memberId + ", amount=" + amount);
            return new Payment("payment-1");
        }
    }

    static class DeliveryService {

        private final List<String> events;

        DeliveryService(List<String> events) {
            this.events = events;
        }

        Delivery request(Long memberId, String reservationId) {
            events.add("delivery requested: memberId=" + memberId + ", reservationId=" + reservationId);
            return new Delivery("delivery-1");
        }
    }

    static class NotificationService {

        private final List<String> events;

        NotificationService(List<String> events) {
            this.events = events;
        }

        void send(Long memberId, String deliveryId) {
            events.add("notification sent: memberId=" + memberId + ", deliveryId=" + deliveryId);
        }
    }
}
