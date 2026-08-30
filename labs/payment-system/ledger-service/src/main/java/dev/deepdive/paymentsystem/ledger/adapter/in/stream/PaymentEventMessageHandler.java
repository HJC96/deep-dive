package dev.deepdive.paymentsystem.ledger.adapter.in.stream;

import dev.deepdive.paymentsystem.ledger.common.StreamAdapter;
import dev.deepdive.paymentsystem.ledger.application.port.in.DoubleLedgerEntryRecordUseCase;
import dev.deepdive.paymentsystem.ledger.domain.LedgerEventMessage;
import dev.deepdive.paymentsystem.ledger.domain.PaymentEventMessage;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Configuration
@StreamAdapter
public class PaymentEventMessageHandler {

    private final DoubleLedgerEntryRecordUseCase doubleLedgerEntryRecordUseCase;
    private final StreamBridge streamBridge;

    public PaymentEventMessageHandler(
        DoubleLedgerEntryRecordUseCase doubleLedgerEntryRecordUseCase,
        StreamBridge streamBridge
    ) {
        this.doubleLedgerEntryRecordUseCase = doubleLedgerEntryRecordUseCase;
        this.streamBridge = streamBridge;
    }

    @Bean
    public Consumer<Message<PaymentEventMessage>> consume() {
        return message -> {
            LedgerEventMessage ledgerEventMessage =
                doubleLedgerEntryRecordUseCase.recordDoubleLedgerEntry(message.getPayload());
            streamBridge.send("ledger", ledgerEventMessage);
        };
    }
}
