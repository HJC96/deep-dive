package dev.deepdive.transaction.msa.wallet.presentation;

import dev.deepdive.transaction.msa.wallet.application.WalletService;
import dev.deepdive.transaction.msa.wallet.presentation.dto.request.WalletDebitRequest;
import dev.deepdive.transaction.msa.wallet.presentation.dto.response.WalletDebitResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/wallets")
public class WalletController {
    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/debit")
    public WalletDebitResponse debit(@Valid @RequestBody WalletDebitRequest request) {
        walletService.debit(request.requestId(), request.userId(), request.amount());
        return new WalletDebitResponse(request.requestId(), true);
    }
}
