package com.example.orderservice.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A deliberate fault, for exercises only (gitops docs/ROLLOUTS.md): the share
 * of API requests answered with 500 instead of being handled, bound from
 * {@code app.fault.*}, e.g. the env var APP_FAULT_ERROR_RATE=0.3.
 *
 * <p>It stands in for a bad release: deploy a version with it set and the
 * canary's analysis sees its errors and rolls it back. Off (0) unless an
 * environment sets it; nothing in the gitops repo does by default.
 *
 * @param errorRate share of API requests to fail, from 0 (never, the
 *                  default) to 1 (always)
 */
@ConfigurationProperties("app.fault")
public record FaultInjectionProperties(double errorRate) {

    public FaultInjectionProperties {
        if (Double.isNaN(errorRate) || errorRate < 0 || errorRate > 1) {
            throw new IllegalArgumentException("app.fault.error-rate must be between 0 and 1, not " + errorRate);
        }
    }

    public boolean enabled() {
        return errorRate > 0;
    }
}
