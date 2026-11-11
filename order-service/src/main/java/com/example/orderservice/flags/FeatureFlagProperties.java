package com.example.orderservice.flags;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Where to ask Unleash for flags, from {@code app.flags.*} (env UNLEASH_URL,
 * UNLEASH_API_TOKEN). Without a URL there is no flag service: every flag
 * takes its fallback.
 *
 * @param url           Unleash's client API, e.g. http://unleash.feature-flags:4242/flags/api/
 * @param token         a backend API token for this environment
 * @param fetchInterval how often the flags are fetched again (default 10s)
 */
@ConfigurationProperties("app.flags")
public record FeatureFlagProperties(String url, String token, Duration fetchInterval) {

    public FeatureFlagProperties {
        if (fetchInterval == null || fetchInterval.isZero() || fetchInterval.isNegative()) {
            fetchInterval = Duration.ofSeconds(10);
        }
    }

    public boolean configured() {
        return url != null && !url.isBlank();
    }
}
