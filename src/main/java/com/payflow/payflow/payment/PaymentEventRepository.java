package com.payflow.payflow.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    long countByPaymentIdAndToStatus(String paymentId, PaymentStatus toStatus);
    List<PaymentEvent> findByPaymentIdOrderByIdAsc(String paymentId);
}