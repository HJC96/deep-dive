package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.common.UseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutCommand;
import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutUseCase;
import dev.deepdive.paymentsystem.payment.application.port.out.LoadProductPort;
import dev.deepdive.paymentsystem.payment.application.port.out.SavePaymentPort;
import dev.deepdive.paymentsystem.payment.domain.CheckoutResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import dev.deepdive.paymentsystem.payment.domain.PaymentOrder;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import dev.deepdive.paymentsystem.payment.domain.Product;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@UseCase
public class CheckoutService implements CheckoutUseCase {

    private final LoadProductPort loadProductPort;
    private final SavePaymentPort savePaymentPort;

    public CheckoutService(LoadProductPort loadProductPort, SavePaymentPort savePaymentPort) {
        this.loadProductPort = loadProductPort;
        this.savePaymentPort = savePaymentPort;
    }

    @Override
    public Mono<CheckoutResult> checkout(CheckoutCommand command) {
        return loadProductPort.getProducts(command.cartId(), command.productIds())
                .collectList()
                .map(products -> createPaymentEvent(command, products))
                .flatMap(paymentEvent -> savePaymentPort.save(paymentEvent).thenReturn(paymentEvent))
                .map(paymentEvent -> new CheckoutResult(
                        paymentEvent.totalAmount(),
                        paymentEvent.orderId(),
                        paymentEvent.orderName()
                ));
    }

    private PaymentEvent createPaymentEvent(CheckoutCommand command, List<Product> products) {
        List<PaymentOrder> paymentOrders = products.stream()
                .map(product -> new PaymentOrder(
                        product.sellerId(),
                        command.idempotencyKey(),
                        product.id(),
                        product.amount(),
                        PaymentStatus.NOT_STARTED
                ))
                .toList();

        return new PaymentEvent(
                command.buyerId(),
                products.stream().map(Product::name).collect(Collectors.joining(", ")),
                command.idempotencyKey(),
                paymentOrders
        );
    }
}
