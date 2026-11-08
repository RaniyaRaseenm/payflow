package com.payflow.payflow.payment;

import com.payflow.payflow.provider.MockPaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PaymentLifecycleTest {

    @Mock PaymentRepository payments;

    private final LocalDateTime now = LocalDateTime.of(2026, 11, 8, 12, 0);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);
    private PaymentService service;

    @BeforeEach
    void setUp() {
        lenient().when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        service = new PaymentService(payments, new MockPaymentProvider(), clock);
    }

    private Payment stored(PaymentStatus target, String providerRef) {
        Payment p = new Payment("m1", 49900, "INR", "tok_visa", now);
        p.setProviderRef(providerRef);
        if (target != PaymentStatus.CREATED) {
            p.transitionTo(PaymentStatus.AUTHORIZED, now);
        }
        if (target == PaymentStatus.CAPTURED) {
            p.transitionTo(PaymentStatus.CAPTURED, now);
        }
        lenient().when(payments.findByIdAndMerchantId(p.getId(), "m1")).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void capturingAnAuthorizedPaymentWorks() {
        Payment p = stored(PaymentStatus.AUTHORIZED, "prov_1");
        assertThat(service.capture("m1", p.getId()).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void capturingACreatedPaymentIsRejected() {
        Payment p = stored(PaymentStatus.CREATED, null);
        assertThatThrownBy(() -> service.capture("m1", p.getId()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void captureFailureMovesToFailed() {
        Payment p = stored(PaymentStatus.AUTHORIZED, "prov_capfail_1");
        Payment result = service.capture("m1", p.getId());
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getFailureCode()).isEqualTo("CAPTURE_FAILED");
    }

    @Test
    void voidingAnAuthorizedPaymentWorks() {
        Payment p = stored(PaymentStatus.AUTHORIZED, "prov_1");
        assertThat(service.voidPayment("m1", p.getId()).getStatus()).isEqualTo(PaymentStatus.VOIDED);
    }

    @Test
    void voidingACapturedPaymentIsRejected() {
        Payment p = stored(PaymentStatus.CAPTURED, "prov_1");
        assertThatThrownBy(() -> service.voidPayment("m1", p.getId()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void refundingACapturedPaymentWorks() {
        Payment p = stored(PaymentStatus.CAPTURED, "prov_1");
        assertThat(service.refund("m1", p.getId()).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refundingAnAuthorizedPaymentIsRejected() {
        Payment p = stored(PaymentStatus.AUTHORIZED, "prov_1");
        assertThatThrownBy(() -> service.refund("m1", p.getId()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void aSecondRefundIsRejected() {
        Payment p = stored(PaymentStatus.CAPTURED, "prov_1");
        service.refund("m1", p.getId());
        assertThatThrownBy(() -> service.refund("m1", p.getId()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}