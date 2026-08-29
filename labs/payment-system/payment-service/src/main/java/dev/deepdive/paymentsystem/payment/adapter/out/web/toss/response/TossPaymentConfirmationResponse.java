package dev.deepdive.paymentsystem.payment.adapter.out.web.toss.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Toss `/v1/payments/confirm` 성공 응답. 필요한 필드 위주로 매핑하고 나머지는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentConfirmationResponse(
        String version,
        String paymentKey,
        String type,
        String orderId,
        String orderName,
        String mId,
        String currency,
        String method,
        Long totalAmount,
        Long balanceAmount,
        String status,
        String requestedAt,
        String approvedAt,
        Boolean useEscrow,
        String lastTransactionKey,
        Long suppliedAmount,
        Long vat,
        Boolean cultureExpense,
        Long taxFreeAmount,
        Long taxExemptionAmount,
        List<Cancel> cancels,
        Card card,
        VirtualAccount virtualAccount,
        MobilePhone mobilePhone,
        GiftCertificate giftCertificate,
        Transfer transfer,
        Receipt receipt,
        Checkout checkout,
        EasyPay easyPay,
        String country,
        TossFailureResponse failure,
        CashReceipt cashReceipt,
        List<CashReceipt> cashReceipts,
        Discount discount
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cancel(
            Long cancelAmount,
            String cancelReason,
            Long taxFreeAmount,
            Long taxExemptionAmount,
            Long refundableAmount,
            Long easyPayDiscountAmount,
            String canceledAt,
            String transactionKey,
            String receiptKey,
            Boolean isPartialCancelable
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Card(
            Long amount,
            String issuerCode,
            String acquirerCode,
            String number,
            Integer installmentPlanMonths,
            String approveNo,
            Boolean useCardPoint,
            String cardType,
            String ownerType,
            String acquireStatus,
            Boolean isInterestFree,
            String interestPayer
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VirtualAccount(
            String accountType,
            String accountNumber,
            String bankCode,
            String customerName,
            String dueDate,
            String refundStatus,
            Boolean expired,
            String settlementStatus,
            RefundReceiveAccount refundReceiveAccount,
            String secret
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MobilePhone(
            String customerMobilePhone,
            String settlementStatus,
            String receiptUrl
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GiftCertificate(
            String approveNo,
            String settlementStatus
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transfer(
            String bankCode,
            String settlementStatus
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Receipt(String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Checkout(String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EasyPay(
            String provider,
            Long amount,
            Long discountAmount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CashReceipt(
            String type,
            String receiptKey,
            String issueNumber,
            String receiptUrl,
            Long amount,
            Long taxFreeAmount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Discount(Long amount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefundReceiveAccount(
            String bankCode,
            String accountNumber,
            String holderName
    ) {
    }
}
