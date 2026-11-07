package com.payflow.payflow.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyFilterTest {

    @Mock ApiKeyRepository repo;

    private final ApiKeyHasher hasher = new ApiKeyHasher("test-pepper");
    private final ApiKeyGenerator generator = new ApiKeyGenerator();
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 7, 12, 0);
    private ApiKeyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyFilter(repo, hasher);
    }

    private MockHttpServletRequest request(String path, String authHeader) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        if (authHeader != null) req.addHeader("Authorization", authHeader);
        return req;
    }

    @Test
    void validKeyPassesAndSetsMerchant() throws Exception {
        ApiKeyGenerator.GeneratedKey k = generator.generate();
        ApiKey row = new ApiKey("merchant-1", k.prefix(), hasher.hash(k.fullKey()), now);
        when(repo.findByKeyPrefixAndStatus(k.prefix(), ApiKeyStatus.ACTIVE))
                .thenReturn(Optional.of(row));

        MockHttpServletRequest req = request("/v1/payments", "Bearer " + k.fullKey());
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(req.getAttribute(ApiKeyFilter.MERCHANT_ATTR)).isEqualTo("merchant-1");
    }

    @Test
    void missingKeyIsRejected() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("/v1/payments", null), res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void unknownOrRevokedKeyIsRejected() throws Exception {
        ApiKeyGenerator.GeneratedKey k = generator.generate();
        when(repo.findByKeyPrefixAndStatus(k.prefix(), ApiKeyStatus.ACTIVE))
                .thenReturn(Optional.empty());

        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("/v1/payments", "Bearer " + k.fullKey()), res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void wrongSecretIsRejected() throws Exception {
        ApiKeyGenerator.GeneratedKey k = generator.generate();
        ApiKey row = new ApiKey("merchant-1", k.prefix(), hasher.hash("some-other-key"), now);
        when(repo.findByKeyPrefixAndStatus(k.prefix(), ApiKeyStatus.ACTIVE))
                .thenReturn(Optional.of(row));

        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("/v1/payments", "Bearer " + k.fullKey()), res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void nonV1PathsAreNotChecked() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("/admin/merchants", null), res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }
}