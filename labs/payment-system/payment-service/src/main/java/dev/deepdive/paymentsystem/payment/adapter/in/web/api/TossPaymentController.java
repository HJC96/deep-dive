package dev.deepdive.paymentsystem.payment.adapter.in.web.api;

import dev.deepdive.paymentsystem.common.WebAdapter;
import dev.deepdive.paymentsystem.payment.adapter.in.web.request.TossPaymentConfirmRequest;
import dev.deepdive.paymentsystem.payment.adapter.in.web.response.ApiResponse;
import dev.deepdive.paymentsystem.payment.adapter.out.web.executor.TossPaymentExecutor;
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

    private final TossPaymentExecutor tossPaymentExecutor;

    public TossPaymentController(TossPaymentExecutor tossPaymentExecutor) {
        this.tossPaymentExecutor = tossPaymentExecutor;
    }

    @PostMapping("/confirm")
    public Mono<ResponseEntity<ApiResponse<String>>> confirm(@RequestBody TossPaymentConfirmRequest request) {
        return tossPaymentExecutor.execute(
                request.paymentKey(),
                request.orderId(),
                String.valueOf(request.amount())
        ).map(it -> ResponseEntity.ok().body(ApiResponse.with(HttpStatus.OK, "", it)));
    }
}
