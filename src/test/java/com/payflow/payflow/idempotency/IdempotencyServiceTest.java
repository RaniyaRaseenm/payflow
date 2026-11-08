package com.payflow.payflow.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock IdempotencyRepository repo;
    @Mock PlatformTransactionManager tm;

    private final Clock clock =
            Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 7, 12, 0);
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repo, tm, clock);
    }

    private IdempotencyRecord existing(String hash) {
        return new IdempotencyRecord("m1", "k1", hash, now, now.plusHours(24));
    }

    @Test
    void firstRequestRunsActionOnceAndSavesCompleted() {
        when(repo.saveAndFlush(any(IdempotencyRecord.class))).thenAnswer(i -> i.getArgument(0));
        AtomicInteger runs = new AtomicInteger();

        IdempotencyService.Result r = service.execute("m1", "k1", "h1",
                () -> { runs.incrementAndGet(); return "pay-1"; });

        assertThat(r.paymentId()).isEqualTo("pay-1");
        assertThat(r.replayed()).isFalse();
        assertThat(runs.get()).isEqualTo(1);
        verify(repo).save(any(IdempotencyRecord.class));
    }

    @Test
    void completedKeyWithSameRequestIsReplayedWithoutRunningAction() {
        IdempotencyRecord done = existing("h1");
        done.markCompleted("pay-1", 201);
        when(repo.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(repo.findByMerchantIdAndIdemKey("m1", "k1")).thenReturn(Optional.of(done));
        AtomicInteger runs = new AtomicInteger();

        IdempotencyService.Result r = service.execute("m1", "k1", "h1",
                () -> { runs.incrementAndGet(); return "pay-2"; });

        assertThat(r.paymentId()).isEqualTo("pay-1");
        assertThat(r.replayed()).isTrue();
        assertThat(runs.get()).isZero();
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() {
        when(repo.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(repo.findByMerchantIdAndIdemKey("m1", "k1"))
                .thenReturn(Optional.of(existing("h1")));

        assertThatThrownBy(() -> service.execute("m1", "k1", "DIFFERENT", () -> "pay-2"))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void keyStillInProgressReturnsRequestInProgress() {
        when(repo.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(repo.findByMerchantIdAndIdemKey("m1", "k1"))
                .thenReturn(Optional.of(existing("h1")));

        assertThatThrownBy(() -> service.execute("m1", "k1", "h1", () -> "pay-2"))
                .isInstanceOf(RequestInProgressException.class);
    }

    @Test
    void failedActionReleasesKeySoClientCanRetry() {
        IdempotencyRecord row = existing("h1");
        when(repo.saveAndFlush(any(IdempotencyRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(repo.findByMerchantIdAndIdemKey("m1", "k1")).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.execute("m1", "k1", "h1",
                () -> { throw new IllegalStateException("provider down"); }))
                .isInstanceOf(IllegalStateException.class);

        verify(repo).delete(row);
        verify(repo, never()).save(any(IdempotencyRecord.class));

    }
}