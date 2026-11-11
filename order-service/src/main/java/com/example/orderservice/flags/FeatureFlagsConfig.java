package com.example.orderservice.flags;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.util.UnleashConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;

/**
 * FeatureFlags backed by Unleash when app.flags.url is set, otherwise every
 * flag takes its fallback. The Unleash client fetches all flags in the
 * background and answers from memory, so a check costs nothing and never
 * waits on the network; it doesn't block start-up either.
 */
@Configuration
@EnableConfigurationProperties(FeatureFlagProperties.class)
public class FeatureFlagsConfig {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagsConfig.class);

    @Bean
    public FeatureFlags featureFlags(FeatureFlagProperties properties,
                                     @Value("${spring.application.name}") String appName) {
        if (!properties.configured()) {
            log.info("No flag service (app.flags.url): every feature flag takes its fallback");
            return (flag, fallback) -> fallback;
        }
        UnleashConfig config = UnleashConfig.builder()
                .appName(appName)
                .instanceId(hostname())
                .unleashAPI(properties.url())
                .apiKey(properties.token())
                .fetchTogglesInterval(properties.fetchInterval().toSeconds())
                .synchronousFetchOnInitialisation(false)
                .build();
        return new UnleashFeatureFlags(new DefaultUnleash(config));
    }

    /** Stops the client's background threads with the application. */
    record UnleashFeatureFlags(Unleash unleash) implements FeatureFlags, DisposableBean {
        @Override
        public boolean isEnabled(String flag, boolean fallback) {
            return unleash.isEnabled(flag, fallback);
        }

        @Override
        public void destroy() {
            unleash.shutdown();
        }
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
