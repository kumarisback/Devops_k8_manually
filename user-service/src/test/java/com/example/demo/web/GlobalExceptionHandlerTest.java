package com.example.demo.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class FailingController {
        @GetMapping("/bad-credentials")
        void badCredentials() {
            throw new BadCredentialsException("Bad credentials");
        }

        @GetMapping("/user-store-down")
        void userStoreDown() {
            throw new InternalAuthenticationServiceException("Timed out after 30000 ms while waiting for a server");
        }

        @GetMapping("/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("Access Denied");
        }

        @GetMapping("/gone")
        void gone() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        @PostMapping("/echo")
        Map<String, String> echo(@RequestBody Map<String, String> body) {
            return body;
        }

        @GetMapping("/bug")
        void bug() {
            throw new IllegalStateException("boom");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void failedSignInIsUnauthorized() throws Exception {
        mvc.perform(get("/bad-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication failed"));
    }

    @Test
    void signInThatCouldNotBeCheckedIsInternalServerError() throws Exception {
        mvc.perform(get("/user-store-down"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("internal_server_error"));
    }

    @Test
    void deniedMethodCallIsForbidden() throws Exception {
        mvc.perform(get("/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void responseStatusExceptionKeepsItsStatus() throws Exception {
        mvc.perform(get("/gone")).andExpect(status().isNotFound());
    }

    @Test
    void unknownPathIsNotFound() throws Exception {
        mvc.perform(get("/no-such-path")).andExpect(status().isNotFound());
    }

    @Test
    void malformedBodyIsBadRequest() throws Exception {
        mvc.perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mvc.perform(delete("/echo")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void anythingElseIsInternalServerError() throws Exception {
        mvc.perform(get("/bug"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("internal_server_error"));
    }
}
