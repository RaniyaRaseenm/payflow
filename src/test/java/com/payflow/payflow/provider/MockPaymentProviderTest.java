package com.payflow.payflow.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockPaymentProviderTest {

    private final MockPaymentProvider provider = new MockPaymentProvider();

    @Test
    void visaSucceeds() {
        ProviderResult r = provider.authorize("k1", 100, "INR", "tok_visa");
        assertThat(r.success()).isTrue();
        assertThat(r.providerRef()).startsWith("prov_");
    }

    @Test
    void declinedCardFails() {
        ProviderResult r = provider.authorize("k2", 100, "INR", "tok_declined");
        assertThat(r.success()).isFalse();
        assertThat(r.failureCode()).isEqualTo("CARD_DECLINED");
    }

    @Test
    void insufficientFundsFails() {
        ProviderResult r = provider.authorize("k3", 100, "INR", "tok_insufficient_funds");
        assertThat(r.failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void sameKeyAlwaysGivesSameAnswer() {
        ProviderResult first = provider.authorize("same", 100, "INR", "tok_visa");
        ProviderResult second = provider.authorize("same", 100, "INR", "tok_visa");
        assertThat(second.providerRef()).isEqualTo(first.providerRef());
    }

    @Test
    void flakyCardFailsTwiceThenSucceeds() {
        assertThatThrownBy(() -> provider.authorize("f1", 100, "INR", "tok_flaky"))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThatThrownBy(() -> provider.authorize("f2", 100, "INR", "tok_flaky"))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThat(provider.authorize("f3", 100, "INR", "tok_flaky").success()).isTrue();
    }

    @Test
    void captureFailsForCaptureFailsToken() {
        ProviderResult auth = provider.authorize("c1", 100, "INR", "tok_capture_fails");
        assertThat(auth.success()).isTrue();
        ProviderResult cap = provider.capture("c1", auth.providerRef());
        assertThat(cap.success()).isFalse();
        assertThat(cap.failureCode()).isEqualTo("CAPTURE_FAILED");
    }
}
