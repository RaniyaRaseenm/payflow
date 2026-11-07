package com.payflow.payflow.payment;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 11, 7, 12, 0);

    private Payment newPayment() {
        return new Payment("m1", 49900, "INR", "tok_visa", now);
    }

    @Test
    void startsAsCreated() {
        assertThat(newPayment().getStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void walksThroughLegalLifecycle() {
        Payment p = newPayment();
        p.transitionTo(PaymentStatus.AUTHORIZED, now);
        p.transitionTo(PaymentStatus.CAPTURED, now);
        p.transitionTo(PaymentStatus.REFUNDED, now);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refundingACreatedPaymentIsRejected() {
        Payment p = newPayment();
        assertThatThrownBy(() -> p.transitionTo(PaymentStatus.REFUNDED, now))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void secondRefundIsRejected() {
        Payment p = newPayment();
        p.transitionTo(PaymentStatus.AUTHORIZED, now);
        p.transitionTo(PaymentStatus.CAPTURED, now);
        p.transitionTo(PaymentStatus.REFUNDED, now);
        assertThatThrownBy(() -> p.transitionTo(PaymentStatus.REFUNDED, now))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}
