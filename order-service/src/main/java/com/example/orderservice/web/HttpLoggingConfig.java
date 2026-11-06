package com.example.orderservice.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({HttpLoggingProperties.class, FaultInjectionProperties.class})
public class HttpLoggingConfig {
}
