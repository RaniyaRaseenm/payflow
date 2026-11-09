package com.payflow.payflow.payment;

import com.payflow.payflow.provider.PaymentProvider;
import com.payflow.payflow.provider.ProviderResult;
import com.payflow.payflow.provider.ProviderUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Service
public class PaymentService {

    private static final int MAX_ATTEMPTS = 3;

    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final Clock clock;
    private final PaymentRecorder recorder;

    /** Plain version (no diary, no webhooks). Keeps the older unit tests working. */
    public PaymentService(PaymentRepository payments, PaymentProvider provider, Clock clock) {
        this(payments, provider, clock, (payment, from) -> payments.save(payment));
    }

    @Autowired
    public PaymentService(PaymentRepository payments, PaymentProvider provider, Clock clock,
                          PaymentRecorder recorder) {
        this.payments = payments;
        this.provider = provider;
        this.clock = clock;
        this.recorder = recorder;
    }

    public Payment create(String merchantId, CreatePaymentRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean capture = request.capture() == null || request.capture();

        Payment payment = recorder.save(new Payment(merchantId, request.amount(),
                request.currency(), request.paymentMethodToken(), now), null);

        ProviderResult auth = provider.authorize("auth_" + payment.getId(),
                payment.getAmountMinor(), payment.getCurrency(), request.paymentMethodToken());

        if (!auth.success()) {
            PaymentStatus before = payment.getStatus();
            payment.transitionTo(PaymentStatus.FAILED, LocalDateTime.now(clock));
            payment.setFailureCode(auth.failureCode());
            return recorder.save(payment, before);
        }

        payment.setProviderRef(auth.providerRef());
        PaymentStatus beforeAuth = payment.getStatus();
        payment.transitionTo(PaymentStatus.AUTHORIZED, LocalDateTime.now(clock));
        payment = recorder.save(payment, beforeAuth);

        if (!capture) {
            return payment;
        }
        return doCapture(payment);
    }

    public Payment capture(String merchantId, String paymentId) {
        return withRetry(() -> {
            Payment payment = get(merchantId, paymentId);
            requireMove(payment, PaymentStatus.CAPTURED);
            return doCapture(payment);
        });
    }

    public Payment voidPayment(String merchantId, String paymentId) {
        return withRetry(() -> {
            Payment payment = get(merchantId, paymentId);
            requireMove(payment, PaymentStatus.VOIDED);
            ProviderResult res = provider.voidAuthorization("void_" + payment.getId(),
                    payment.getProviderRef());
            if (!res.success()) {
                throw new ProviderUnavailableException("Void failed: " + res.failureCode());
            }
            PaymentStatus before = payment.getStatus();
            payment.transitionTo(PaymentStatus.VOIDED, LocalDateTime.now(clock));
            return recorder.save(payment, before);
        });
    }

    public Payment refund(String merchantId, String paymentId) {
        return withRetry(() -> {
            Payment payment = get(merchantId, paymentId);
            requireMove(payment, PaymentStatus.REFUNDED);
            ProviderResult res = provider.refund("refund_" + payment.getId(),
                    payment.getProviderRef());
            if (!res.success()) {
                throw new ProviderUnavailableException("Refund failed: " + res.failureCode());
            }
            PaymentStatus before = payment.getStatus();
            payment.transitionTo(PaymentStatus.REFUNDED, LocalDateTime.now(clock));
            return recorder.save(payment, before);
        });
    }

    public Payment get(String merchantId, String paymentId) {
        return payments.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private Payment doCapture(Payment payment) {
        ProviderResult cap = provider.capture("cap_" + payment.getId(), payment.getProviderRef());
        PaymentStatus before = payment.getStatus();
        if (cap.success()) {
            payment.transitionTo(PaymentStatus.CAPTURED, LocalDateTime.now(clock));
        } else {
            payment.transitionTo(PaymentStatus.FAILED, LocalDateTime.now(clock));
            payment.setFailureCode(cap.failureCode());
        }
        return recorder.save(payment, before);
    }

    private void requireMove(Payment payment, PaymentStatus next) {
        if (!payment.getStatus().canMoveTo(next)) {
            throw new InvalidStateTransitionException(payment.getStatus(), next);
        }
    }

    /** If someone else changed the payment first, reload it, re-check the rules, and try again. */
    private Payment withRetry(Supplier<Payment> operation) {
        for (int attempt = 1; ; attempt++) {
            try {
                return operation.get();
            } catch (OptimisticLockingFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new PaymentConflictException();
                }
                pause();
            }
        }
    }

    private void pause() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 25));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new PaymentConflictException();
        }
    }
}