package com.example.orderservice.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the API request log (RequestLoggingFilter), bound from
 * {@code app.http-logging.*}, e.g. the env var APP_HTTP_LOGGING_REQUEST_BODY
 * that each environment's overlay sets in the gitops repo.
 *
 * @param requestBody   when to include the request payload: NEVER (default),
 *                      ERRORS (only for 4xx/5xx responses) or ALWAYS
 * @param maxBodyLength longest payload logged, in characters; longer ones are
 *                      cut and marked (default 2048)
 */
@ConfigurationProperties("app.http-logging")
public record HttpLoggingProperties(RequestBody requestBody, int maxBodyLength) {

    public enum RequestBody { NEVER, ERRORS, ALWAYS }

    public HttpLoggingProperties {
        if (requestBody == null) {
            requestBody = RequestBody.NEVER;
        }
        if (maxBodyLength <= 0) {
            maxBodyLength = 2048;
        }
    }
}
