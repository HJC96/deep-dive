package dev.deepdive.paymentsystem.payment.adapter.in.web.view;

import dev.deepdive.paymentsystem.common.IdempotencyCreator;
import dev.deepdive.paymentsystem.common.WebAdapter;
import dev.deepdive.paymentsystem.payment.adapter.in.web.request.CheckoutRequest;
import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutCommand;
import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;

@WebAdapter
@Controller
public class CheckoutController {

    private final CheckoutUseCase checkoutUseCase;

    public CheckoutController(CheckoutUseCase checkoutUseCase) {
        this.checkoutUseCase = checkoutUseCase;
    }

    @GetMapping("/")
    public Mono<String> checkoutPage(CheckoutRequest request, Model model) {
        CheckoutCommand command = new CheckoutCommand(
                request.cartId(),
                request.buyerId(),
                request.productIds(),
                IdempotencyCreator.create(request)
        );

        return checkoutUseCase.checkout(command)
                .map(result -> {
                    model.addAttribute("orderId", result.orderId());
                    model.addAttribute("orderName", result.orderName());
                    model.addAttribute("amount", result.amount());
                    return "checkout";
                });
    }
}
