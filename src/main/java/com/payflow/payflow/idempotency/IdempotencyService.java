package com.payflow.payflow.idempotency;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    public record Result(String paymentId, boolean replayed) { }

    private final IdempotencyRepository repo;
    private final TransactionTemplate tx;
    private final Clock clock;

    public IdempotencyService(IdempotencyRepository repo,
                              PlatformTransactionManager transactionManager, Clock clock) {
        this.repo = repo;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** action creates the payment and returns its id. It runs at most once per key. */
    public Result execute(String merchantId, String key, String requestHash,
                          Supplier<String> action) {
        LocalDateTime now = LocalDateTime.now(clock);
        IdempotencyRecord record;
        try {
            // Claim the ticket. The unique key lets exactly one request win.
            record = tx.execute(s -> repo.saveAndFlush(
                    new IdempotencyRecord(merchantId, key, requestHash, now, now.plusHours(24))));
        } catch (DataIntegrityViolationException duplicate) {
            return answerFromExisting(merchantId, key, requestHash);
        }

        String paymentId;
        try {
            paymentId = action.get();
        } catch (RuntimeException e) {
            release(merchantId, key);   // let the client retry with the same key
            throw e;
        }

        record.markCompleted(paymentId, 201);
        IdempotencyRecord done = record;
        tx.executeWithoutResult(s -> repo.save(done));
        return new Result(paymentId, false);
    }

    private Result answerFromExisting(String merchantId, String key, String requestHash) {
        IdempotencyRecord existing = repo.findByMerchantIdAndIdemKey(merchantId, key)
                .orElseThrow(RequestInProgressException::new);
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
            return new Result(existing.getPaymentId(), true);
        }
        throw new RequestInProgressException();
    }

    private void release(String merchantId, String key) {
        tx.executeWithoutResult(s ->
                repo.findByMerchantIdAndIdemKey(merchantId, key).ifPresent(repo::delete));
    }
}
