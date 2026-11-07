package com.payflow.payflow.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyTest {

    private final ApiKeyGenerator generator = new ApiKeyGenerator();
    private final ApiKeyHasher hasher = new ApiKeyHasher("pepper-one");

    @Test
    void generatedKeyHasExpectedFormat() {
        ApiKeyGenerator.GeneratedKey k = generator.generate();
        assertThat(k.fullKey()).matches("pf_live_[A-Za-z0-9]{8}_[A-Za-z0-9]{32}");
        assertThat(ApiKeyGenerator.extractPrefix(k.fullKey())).isEqualTo(k.prefix());
    }

    @Test
    void twoGeneratedKeysDiffer() {
        assertThat(generator.generate().fullKey())
                .isNotEqualTo(generator.generate().fullKey());
    }

    @Test
    void malformedKeyHasNoPrefix() {
        assertThat(ApiKeyGenerator.extractPrefix("hello")).isNull();
        assertThat(ApiKeyGenerator.extractPrefix(null)).isNull();
    }

    @Test
    void hashIsDeterministicAnd64Chars() {
        String h = hasher.hash("some-key");
        assertThat(h).hasSize(64).isEqualTo(hasher.hash("some-key"));
    }

    @Test
    void correctKeyMatchesAndWrongKeyDoesNot() {
        String h = hasher.hash("right-key");
        assertThat(hasher.matches("right-key", h)).isTrue();
        assertThat(hasher.matches("wrong-key", h)).isFalse();
    }

    @Test
    void differentPepperGivesDifferentHash() {
        assertThat(new ApiKeyHasher("pepper-two").hash("k"))
                .isNotEqualTo(hasher.hash("k"));
    }
}