package com.payflow.payflow.payment;

import com.payflow.payflow.merchant.Merchant;
import com.payflow.payflow.merchant.MerchantRepository;
import com.payflow.payflow.webhook.WebhookDelivery;
import com.payflow.payflow.webhook.WebhookDeliveryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
public class TransactionalPaymentRecorder implements PaymentRecorder {

    private final PaymentRepository payments;
    private final PaymentEventRepository events;
    private final WebhookDeliveryRepository deliveries;
    private final MerchantRepository merchants;
    private final Clock clock;

    public TransactionalPaymentRecorder(PaymentRepository payments, PaymentEventRepository events,
                                        WebhookDeliveryRepository deliveries,
                                        MerchantRepository merchants, Clock clock) {
        this.payments = payments;
        this.events = events;
        this.deliveries = deliveries;
        this.merchants = merchants;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Payment save(Payment payment, PaymentStatus from) {
        // flush now, so a version clash is thrown here and the whole step is rolled back
        Payment saved = payments.saveAndFlush(payment);
        LocalDateTime now = LocalDateTime.now(clock);

        events.save(new PaymentEvent(saved.getId(), from, saved.getStatus(),
                saved.getFailureCode(), now));

        String type = webhookTypeFor(saved.getStatus());
        if (type == null) {
            return saved;
        }
        Optional<Merchant> merchant = merchants.findById(saved.getMerchantId());
        if (merchant.isEmpty() || isBlank(merchant.get().getWebhookUrl())) {
            return saved;
        }

        String eventId = "evt_" + UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\","
                + "\"type\":\"" + type + "\","
                + "\"paymentId\":\"" + saved.getId() + "\","
                + "\"status\":\"" + saved.getStatus().name() + "\","
                + "\"version\":" + saved.getVersion() + ","
                + "\"occurredAt\":\"" + now + "Z\"}";

        deliveries.save(new WebhookDelivery(saved.getMerchantId(), saved.getId(), eventId,
                type, payload, merchant.get().getWebhookUrl(), now));
        return saved;
    }

    private static String webhookTypeFor(PaymentStatus status) {
        return switch (status) {
            case AUTHORIZED -> "payment.authorized";
            case CAPTURED -> "payment.captured";
            case FAILED -> "payment.failed";
            case VOIDED -> "payment.voided";
            case REFUNDED -> "payment.refunded";
            case CREATED -> null;
        };
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
