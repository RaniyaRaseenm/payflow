package com.payflow.payflow.merchant;

import com.payflow.payflow.auth.ApiKey;
import com.payflow.payflow.auth.ApiKeyGenerator;
import com.payflow.payflow.auth.ApiKeyHasher;
import com.payflow.payflow.auth.ApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock MerchantRepository merchants;
    @Mock ApiKeyRepository apiKeys;

    private final ApiKeyGenerator generator = new ApiKeyGenerator();
    private final ApiKeyHasher hasher = new ApiKeyHasher("test-pepper");
    private final Clock clock =
            Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);
    private MerchantService service;

    @BeforeEach
    void setUp() {
        service = new MerchantService(merchants, apiKeys, generator, hasher, clock);
    }

    private CreateMerchantRequest request() {
        return new CreateMerchantRequest("Shop", "shop@example.com", null);
    }

    @Test
    void createsMerchantAndReturnsKey() {
        when(merchants.save(any(Merchant.class))).thenAnswer(i -> i.getArgument(0));
        CreateMerchantResponse r = service.createMerchant(request());
        assertThat(r.apiKey()).matches("pf_live_[A-Za-z0-9]{8}_[A-Za-z0-9]{32}");
        assertThat(r.webhookSecret()).startsWith("whsec_");
    }

    @Test
    void storesOnlyTheHashNeverTheRealKey() {
        when(merchants.save(any(Merchant.class))).thenAnswer(i -> i.getArgument(0));
        CreateMerchantResponse r = service.createMerchant(request());

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeys).save(captor.capture());
        ApiKey saved = captor.getValue();

        assertThat(saved.getKeyHash()).isNotEqualTo(r.apiKey());
        assertThat(hasher.matches(r.apiKey(), saved.getKeyHash())).isTrue();
        assertThat(saved.getMerchantId()).isEqualTo(r.merchantId());
    }

    @Test
    void duplicateEmailIsRejected() {
        when(merchants.existsByEmail("shop@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.createMerchant(request()))
                .isInstanceOf(ResponseStatusException.class);
        verify(merchants, never()).save(any());
    }
}
