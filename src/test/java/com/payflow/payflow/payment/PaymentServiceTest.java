package com.payflow.payflow.payment;

import com.payflow.payflow.provider.MockPaymentProvider;
import com.payflow.payflow.provider.ProviderUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository payments;

    private final MockPaymentProvider provider = new MockPaymentProvider();
    private final Clock clock =
            Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);
    private PaymentService service;

    @BeforeEach
    void setUp() {
        lenient().when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        service = new PaymentService(payments, provider, clock);
    }

    private CreatePaymentRequest request(String token, Boolean capture) {
        return new CreatePaymentRequest(49900L, "INR", token, capture);
    }

    @Test
    void visaIsCapturedByDefault() {
        Payment p = service.create("m1", request("tok_visa", null));
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(p.getProviderRef()).startsWith("prov_");
        assertThat(p.getAmountMinor()).isEqualTo(49900L);
    }

    @Test
    void captureFalseStopsAtAuthorized() {
        Payment p = service.create("m1", request("tok_visa", false));
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @Test
    void declinedCardEndsFailed() {
        Payment p = service.create("m1", request("tok_declined", null));
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(p.getFailureCode()).isEqualTo("CARD_DECLINED");
    }

    @Test
    void captureFailureEndsFailed() {
        Payment p = service.create("m1", request("tok_capture_fails", null));
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(p.getFailureCode()).isEqualTo("CAPTURE_FAILED");
    }

    @Test
    void providerDownLeavesPaymentCreated() {
        assertThatThrownBy(() -> service.create("m1", request("tok_flaky", null)))
                .isInstanceOf(ProviderUnavailableException.class);
        verify(payments, times(1)).save(any(Payment.class));
    }

    @Test
    void getReturnsOwnPayment() {
        Payment own = new Payment("m1", 100, "INR", "tok_visa",
                java.time.LocalDateTime.of(2026, 11, 7, 12, 0));
        when(payments.findByIdAndMerchantId(own.getId(), "m1")).thenReturn(Optional.of(own));
        assertThat(service.get("m1", own.getId())).isSameAs(own);
    }

    @Test
    void otherMerchantsPaymentIsNotFound() {
        assertThatThrownBy(() -> service.get("m2", "some-payment-id"))
                .isInstanceOf(PaymentNotFoundException.class);
    }
}
