package com.example.orderservice.flags;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagsConfigTest {

    private final FeatureFlagsConfig config = new FeatureFlagsConfig();

    @Test
    void withoutAFlagServiceEveryFlagTakesItsFallback() {
        FeatureFlags flags = config.featureFlags(new FeatureFlagProperties("", null, null), "order-service");

        assertThat(flags.isEnabled(FeatureFlags.ORDER_EVENTS_PUBLISHING, true)).isTrue();
        assertThat(flags.isEnabled("anything", false)).isFalse();
    }

    @Test
    void anUnreachableFlagServiceMeansTheFallbackToo() throws Exception {
        // Nothing listens on port 9: the client can't fetch, and must not
        // switch anything off because of it.
        FeatureFlags flags = config.featureFlags(
                new FeatureFlagProperties("http://127.0.0.1:9/api/", "default:development.test", Duration.ofSeconds(1)),
                "order-service");
        try {
            assertThat(flags).isInstanceOf(FeatureFlagsConfig.UnleashFeatureFlags.class);
            assertThat(flags.isEnabled(FeatureFlags.ORDER_EVENTS_PUBLISHING, true)).isTrue();
        } finally {
            ((FeatureFlagsConfig.UnleashFeatureFlags) flags).destroy();
        }
    }

    @Test
    void defaultsToFetchingEveryTenSeconds() {
        assertThat(new FeatureFlagProperties("http://unleash/api/", "t", null).fetchInterval()).isEqualTo(Duration.ofSeconds(10));
    }
}
