package com.payflow.payflow.payment;

import com.payflow.payflow.provider.PaymentProvider;
import com.payflow.payflow.provider.ProviderResult;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, PaymentProvider provider, Clock clock) {
        this.payments = payments;
        this.provider = provider;
        this.clock = clock;
    }

    public Payment create(String merchantId, CreatePaymentRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean capture = request.capture() == null || request.capture();

        Payment payment = payments.save(new Payment(merchantId, request.amount(),
                request.currency(), request.paymentMethodToken(), now));

        ProviderResult auth = provider.authorize("auth_" + payment.getId(),
                payment.getAmountMinor(), payment.getCurrency(), request.paymentMethodToken());

        if (!auth.success()) {
            payment.transitionTo(PaymentStatus.FAILED, LocalDateTime.now(clock));
            payment.setFailureCode(auth.failureCode());
            return payments.save(payment);
        }

        payment.setProviderRef(auth.providerRef());
        payment.transitionTo(PaymentStatus.AUTHORIZED, LocalDateTime.now(clock));
        payment = payments.save(payment);

        if (!capture) {
            return payment;
        }

        ProviderResult cap = provider.capture("cap_" + payment.getId(), payment.getProviderRef());
        if (cap.success()) {
            payment.transitionTo(PaymentStatus.CAPTURED, LocalDateTime.now(clock));
        } else {
            payment.transitionTo(PaymentStatus.FAILED, LocalDateTime.now(clock));
            payment.setFailureCode(cap.failureCode());
        }
        return payments.save(payment);
    }

    public Payment get(String merchantId, String paymentId) {
        return payments.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}