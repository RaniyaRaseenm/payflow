package com.payflow.payflow.merchant;

import com.payflow.payflow.auth.ApiKey;
import com.payflow.payflow.auth.ApiKeyGenerator;
import com.payflow.payflow.auth.ApiKeyHasher;
import com.payflow.payflow.auth.ApiKeyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class MerchantService {

    private final MerchantRepository merchants;
    private final ApiKeyRepository apiKeys;
    private final ApiKeyGenerator generator;
    private final ApiKeyHasher hasher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public MerchantService(MerchantRepository merchants, ApiKeyRepository apiKeys,
                           ApiKeyGenerator generator, ApiKeyHasher hasher, Clock clock) {
        this.merchants = merchants;
        this.apiKeys = apiKeys;
        this.generator = generator;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Transactional
    public CreateMerchantResponse createMerchant(CreateMerchantRequest request) {
        if (merchants.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        LocalDateTime now = LocalDateTime.now(clock);

        Merchant merchant = merchants.save(new Merchant(
                request.name(), request.email(), request.webhookUrl(),
                newWebhookSecret(), now));

        ApiKeyGenerator.GeneratedKey key = generator.generate();
        apiKeys.save(new ApiKey(merchant.getId(), key.prefix(),
                hasher.hash(key.fullKey()), now));

        return new CreateMerchantResponse(merchant.getId(), merchant.getName(),
                merchant.getEmail(), merchant.getWebhookSecret(), key.fullKey());
    }

    private String newWebhookSecret() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return "whsec_" + HexFormat.of().formatHex(bytes);
    }
}
