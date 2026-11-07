package com.payflow.payflow.payment;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {

    @ParameterizedTest
    @CsvSource({"CREATED,AUTHORIZED", "CREATED,FAILED", "AUTHORIZED,CAPTURED",
            "AUTHORIZED,VOIDED", "AUTHORIZED,FAILED", "CAPTURED,REFUNDED"})
    void legalMovesAreAllowed(PaymentStatus from, PaymentStatus to) {
        assertThat(from.canMoveTo(to)).isTrue();
    }

    @Test
    void everyOtherMoveIsRejected() {
        Set<String> legal = Set.of("CREATED>AUTHORIZED", "CREATED>FAILED",
                "AUTHORIZED>CAPTURED", "AUTHORIZED>VOIDED", "AUTHORIZED>FAILED",
                "CAPTURED>REFUNDED");
        for (PaymentStatus from : PaymentStatus.values()) {
            for (PaymentStatus to : PaymentStatus.values()) {
                if (!legal.contains(from + ">" + to)) {
                    assertThat(from.canMoveTo(to)).isFalse();
                }
            }
        }
    }

    @Test
    void terminalStatesAcceptNothing() {
        assertThat(PaymentStatus.FAILED.isTerminal()).isTrue();
        assertThat(PaymentStatus.VOIDED.isTerminal()).isTrue();
        assertThat(PaymentStatus.REFUNDED.isTerminal()).isTrue();
        assertThat(PaymentStatus.CAPTURED.isTerminal()).isFalse();
    }
}
