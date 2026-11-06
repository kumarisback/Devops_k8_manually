package com.example.demo.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The gateway limits sign-in and sign-up attempts by path (gitops
 * apps/base/routes.yaml: everything under /api/auth). Another spelling of the
 * same endpoint, which the gateway would route without the limit, must not
 * reach the handler here: path parameters (";...") are refused by Spring
 * Security's firewall, and a trailing slash is a different path.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthPathVariantsTest {

    @DynamicPropertySource
    static void signingKey(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.signing-key", TestKeys::rsaPkcs8Pem);
    }

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/signin;x=1", "/api/auth;x=1/signin", "/api/auth/signup;jsessionid=1"})
    void pathParametersAreRefused(String uri) throws Exception {
        mvc.perform(post(uri).contextPath("/api").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/signin/", "/api/auth/signup/"})
    void aTrailingSlashIsNotTheEndpoint(String uri) throws Exception {
        mvc.perform(post(uri).contextPath("/api").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    // The control: the same setup does reach this controller under /api/auth,
    // so the 404s above mean "no such path", not a misconfigured test.
    @Test
    void theRealPathsReachTheController() throws Exception {
        mvc.perform(get("/api/auth/jwks.json").contextPath("/api")).andExpect(status().isOk());
    }
}
