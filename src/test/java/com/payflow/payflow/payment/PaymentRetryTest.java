package com.payflow.payflow.payment;

import com.payflow.payflow.provider.MockPaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRetryTest {

    @Mock PaymentRepository payments;

    private final LocalDateTime now = LocalDateTime.of(2026, 11, 8, 12, 0);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);
    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, new MockPaymentProvider(), clock);
    }

    private Payment captured() {
        Payment p = new Payment("m1", 49900, "INR", "tok_visa", now);
        p.setProviderRef("prov_1");
        p.transitionTo(PaymentStatus.AUTHORIZED, now);
        p.transitionTo(PaymentStatus.CAPTURED, now);
        return p;
    }

    private Payment refunded() {
        Payment p = captured();
        p.transitionTo(PaymentStatus.REFUNDED, now);
        return p;
    }

    private ObjectOptimisticLockingFailureException clash() {
        return new ObjectOptimisticLockingFailureException(Payment.class, "x");
    }

    @Test
    void succeedsAfterOneConflict() {
        when(payments.findByIdAndMerchantId(anyString(), anyString()))
                .thenAnswer(i -> Optional.of(captured()));
        when(payments.save(any(Payment.class)))
                .thenThrow(clash()).thenAnswer(i -> i.getArgument(0));

        Payment result = service.refund("m1", "p1");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(payments, times(2)).save(any(Payment.class));
    }

    @Test
    void afterConflictTheFreshStateMakesRefundIllegal() {
        when(payments.findByIdAndMerchantId(anyString(), anyString()))
                .thenReturn(Optional.of(captured()), Optional.of(refunded()));
        when(payments.save(any(Payment.class))).thenThrow(clash());

        assertThatThrownBy(() -> service.refund("m1", "p1"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void giveUpAfterThreeConflicts() {
        when(payments.findByIdAndMerchantId(anyString(), anyString()))
                .thenAnswer(i -> Optional.of(captured()));
        when(payments.save(any(Payment.class))).thenThrow(clash());

        assertThatThrownBy(() -> service.refund("m1", "p1"))
                .isInstanceOf(PaymentConflictException.class);
        verify(payments, times(3)).save(any(Payment.class));
    }
}
