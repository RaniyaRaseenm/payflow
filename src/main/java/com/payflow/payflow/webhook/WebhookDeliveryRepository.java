package com.payflow.payflow.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
    long countByPaymentId(String paymentId);
    List<WebhookDelivery> findByPaymentIdOrderByIdAsc(String paymentId);
}