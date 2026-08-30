package dev.deepdive.paymentsystem.wallet.adapter.in.stream;

import dev.deepdive.paymentsystem.wallet.common.StreamAdapter;
import dev.deepdive.paymentsystem.wallet.application.port.in.SettlementUseCase;
import dev.deepdive.paymentsystem.wallet.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.wallet.domain.WalletEventMessage;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Configuration
@StreamAdapter
public class PaymentEventMessageHandler {

    private final SettlementUseCase settlementUseCase;
    private final StreamBridge streamBridge;

    public PaymentEventMessageHandler(SettlementUseCase settlementUseCase, StreamBridge streamBridge) {
        this.settlementUseCase = settlementUseCase;
        this.streamBridge = streamBridge;
    }

    @Bean
    public Consumer<Message<PaymentEventMessage>> consume() {
        return message -> {
            WalletEventMessage walletEventMessage = settlementUseCase.processSettlement(message.getPayload());
            streamBridge.send("wallet", walletEventMessage);
        };
    }
}
