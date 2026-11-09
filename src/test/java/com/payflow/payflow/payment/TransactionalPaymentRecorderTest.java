package com.payflow.payflow.payment;

import com.payflow.payflow.merchant.Merchant;
import com.payflow.payflow.merchant.MerchantRepository;
import com.payflow.payflow.webhook.WebhookDelivery;
import com.payflow.payflow.webhook.WebhookDeliveryRepository;
import com.payflow.payflow.webhook.WebhookStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionalPaymentRecorderTest {

    @Mock PaymentRepository payments;
    @Mock PaymentEventRepository events;
    @Mock WebhookDeliveryRepository deliveries;
    @Mock MerchantRepository merchants;

    private final LocalDateTime now = LocalDateTime.of(2026, 11, 9, 12, 0);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);
    private TransactionalPaymentRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new TransactionalPaymentRecorder(payments, events, deliveries, merchants, clock);
        lenient().when(payments.saveAndFlush(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Payment captured() {
        Payment p = new Payment("m1", 49900, "INR", "tok_visa", now);
        p.transitionTo(PaymentStatus.AUTHORIZED, now);
        p.transitionTo(PaymentStatus.CAPTURED, now);
        return p;
    }

    private Merchant merchant(String webhookUrl) {
        return new Merchant("Shop", "shop@example.com", webhookUrl, "whsec_x", now);
    }

    @Test
    void capturedPaymentSavesDiaryEntryAndWebhookRow() {
        when(merchants.findById("m1")).thenReturn(Optional.of(merchant("https://shop.example/hook")));

        recorder.save(captured(), PaymentStatus.AUTHORIZED);

        ArgumentCaptor<PaymentEvent> ev = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(events).save(ev.capture());
        assertThat(ev.getValue().getFromStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(ev.getValue().getToStatus()).isEqualTo(PaymentStatus.CAPTURED);

        ArgumentCaptor<WebhookDelivery> wh = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveries).save(wh.capture());
        assertThat(wh.getValue().getEventType()).isEqualTo("payment.captured");
        assertThat(wh.getValue().getStatus()).isEqualTo(WebhookStatus.PENDING);
        assertThat(wh.getValue().getTargetUrl()).isEqualTo("https://shop.example/hook");
        assertThat(wh.getValue().getEventId()).startsWith("evt_");
        assertThat(wh.getValue().getNextAttemptAt()).isEqualTo(now);
    }

    @Test
    void webhookPayloadCarriesTypeStatusAndVersion() {
        when(merchants.findById("m1")).thenReturn(Optional.of(merchant("https://shop.example/hook")));

        recorder.save(captured(), PaymentStatus.AUTHORIZED);

        ArgumentCaptor<WebhookDelivery> wh = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveries).save(wh.capture());
        String payload = wh.getValue().getPayload();
        assertThat(payload).contains("\"type\":\"payment.captured\"");
        assertThat(payload).contains("\"status\":\"CAPTURED\"");
        assertThat(payload).contains("\"eventId\":\"" + wh.getValue().getEventId() + "\"");
        assertThat(payload).contains("\"version\":");
    }

    @Test
    void merchantWithoutWebhookGetsDiaryButNoWebhookRow() {
        when(merchants.findById("m1")).thenReturn(Optional.of(merchant(null)));

        recorder.save(captured(), PaymentStatus.AUTHORIZED);

        verify(events).save(any(PaymentEvent.class));
        verify(deliveries, never()).save(any(WebhookDelivery.class));
    }

    @Test
    void newCreatedPaymentGetsDiaryButNoWebhook() {
        Payment fresh = new Payment("m1", 100, "INR", "tok_visa", now);

        recorder.save(fresh, null);

        ArgumentCaptor<PaymentEvent> ev = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(events).save(ev.capture());
        assertThat(ev.getValue().getFromStatus()).isNull();
        assertThat(ev.getValue().getToStatus()).isEqualTo(PaymentStatus.CREATED);
        verify(deliveries, never()).save(any(WebhookDelivery.class));
        verify(merchants, never()).findById(any());
    }

    @Test
    void failedPaymentDiaryEntryCarriesTheFailureCode() {
        when(merchants.findById("m1")).thenReturn(Optional.of(merchant(null)));
        Payment p = new Payment("m1", 100, "INR", "tok_declined", now);
        p.transitionTo(PaymentStatus.FAILED, now);
        p.setFailureCode("CARD_DECLINED");

        recorder.save(p, PaymentStatus.CREATED);

        ArgumentCaptor<PaymentEvent> ev = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(events).save(ev.capture());
        assertThat(ev.getValue().getReason()).isEqualTo("CARD_DECLINED");
    }
}
