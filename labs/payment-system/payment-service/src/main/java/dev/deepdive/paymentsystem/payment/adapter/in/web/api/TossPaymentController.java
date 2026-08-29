package dev.deepdive.paymentsystem.payment.adapter.in.web.api;

import dev.deepdive.paymentsystem.common.WebAdapter;
import dev.deepdive.paymentsystem.payment.adapter.in.web.request.TossPaymentConfirmRequest;
import dev.deepdive.paymentsystem.payment.adapter.in.web.response.ApiResponse;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmUseCase;
import dev.deepdive.paymentsystem.payment.domain.PaymentConfirmationResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@WebAdapter
@RequestMapping("/v1/toss")
@RestController
public class TossPaymentController {

    private final PaymentConfirmUseCase paymentConfirmUseCase;

    public TossPaymentController(PaymentConfirmUseCase paymentConfirmUseCase) {
        this.paymentConfirmUseCase = paymentConfirmUseCase;
    }

    @PostMapping("/confirm")
    public Mono<ResponseEntity<ApiResponse<PaymentConfirmationResult>>> confirm(@RequestBody TossPaymentConfirmRequest request) {
        PaymentConfirmCommand command = new PaymentConfirmCommand(
                request.paymentKey(),
                request.orderId(),
                Long.parseLong(request.amount())
        );

        return paymentConfirmUseCase.confirm(command)
                .map(it -> ResponseEntity.ok().body(ApiResponse.with(HttpStatus.OK, "", it)));
    }
}
