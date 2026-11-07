package com.payflow.payflow.payment;

import com.payflow.payflow.auth.ApiKeyFilter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(
            @RequestAttribute(ApiKeyFilter.MERCHANT_ATTR) String merchantId,
            @Valid @RequestBody CreatePaymentRequest request) {
        return PaymentResponse.from(service.create(merchantId, request));
    }

    @GetMapping("/{id}")
    public PaymentResponse get(
            @RequestAttribute(ApiKeyFilter.MERCHANT_ATTR) String merchantId,
            @PathVariable String id) {
        return PaymentResponse.from(service.get(merchantId, id));
    }
}
