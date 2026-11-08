package com.payflow.payflow.payment;

import com.payflow.payflow.auth.ApiKeyFilter;
import com.payflow.payflow.common.Hashing;
import com.payflow.payflow.idempotency.IdempotencyKeyMissingException;
import com.payflow.payflow.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentService service;
    private final IdempotencyService idempotency;

    public PaymentController(PaymentService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestAttribute(ApiKeyFilter.MERCHANT_ATTR) String merchantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new IdempotencyKeyMissingException();
        }

        boolean capture = request.capture() == null || request.capture();
        String requestHash = Hashing.sha256("POST|/v1/payments|" + request.amount() + "|"
                + request.currency() + "|" + request.paymentMethodToken() + "|" + capture);

        IdempotencyService.Result result = idempotency.execute(merchantId, idempotencyKey,
                requestHash, () -> service.create(merchantId, request).getId());

        PaymentResponse body = PaymentResponse.from(service.get(merchantId, result.paymentId()));
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(
            @RequestAttribute(ApiKeyFilter.MERCHANT_ATTR) String merchantId,
            @PathVariable String id) {
        return PaymentResponse.from(service.get(merchantId, id));
    }
}