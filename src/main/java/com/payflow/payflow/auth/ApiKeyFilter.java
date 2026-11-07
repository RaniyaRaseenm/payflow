package com.payflow.payflow.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String MERCHANT_ATTR = "payflow.merchantId";

    private final ApiKeyRepository apiKeys;
    private final ApiKeyHasher hasher;

    public ApiKeyFilter(ApiKeyRepository apiKeys, ApiKeyHasher hasher) {
        this.apiKeys = apiKeys;
        this.hasher = hasher;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        String key = (header != null && header.startsWith("Bearer "))
                ? header.substring(7).trim() : null;

        String prefix = ApiKeyGenerator.extractPrefix(key);
        if (prefix == null) {
            reject(response);
            return;
        }

        Optional<ApiKey> found =
                apiKeys.findByKeyPrefixAndStatus(prefix, ApiKeyStatus.ACTIVE);
        if (found.isEmpty() || !hasher.matches(key, found.get().getKeyHash())) {
            reject(response);
            return;
        }

        request.setAttribute(MERCHANT_ATTR, found.get().getMerchantId());
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\","
                + "\"status\":401,\"code\":\"UNAUTHORIZED\","
                + "\"detail\":\"Missing, unknown or revoked API key\"}");
    }
}
