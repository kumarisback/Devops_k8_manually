package com.example.demo.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    private final JwtUtils jwt = new JwtUtils(TestKeys.rsaPkcs8Pem(), 60_000);

    private String tokenFor(String username) {
        UserDetailsImpl user = new UserDetailsImpl("id-1", username, "hash", List.of());
        return jwt.generateJwtToken(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    void aTokenItIssuedIsValidAndNamesTheUser() {
        String token = tokenFor("asha");

        assertThat(jwt.validateJwtToken(token)).isTrue();
        assertThat(jwt.getUserNameFromJwtToken(token)).isEqualTo("asha");
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejectedNotThrown() {
        JwtUtils someoneElse = new JwtUtils(TestKeys.rsaPkcs8Pem(), 60_000);
        UserDetailsImpl user = new UserDetailsImpl("id-1", "asha", "hash", List.of());
        String forged = someoneElse.generateJwtToken(new UsernamePasswordAuthenticationToken(user, null, List.of()));

        assertThat(jwt.validateJwtToken(forged)).isFalse();
        assertThat(jwt.validateJwtToken("not-a-jwt")).isFalse();
        assertThat(jwt.validateJwtToken("")).isFalse();
    }

    @Test
    void anUnsignedTokenIsRejected() {
        String unsigned = Jwts.builder().issuer(JwtUtils.ISSUER).subject("admin")
                .expiration(new Date(System.currentTimeMillis() + 60_000)).compact();

        assertThat(jwt.validateJwtToken(unsigned)).isFalse();
    }

    @Test
    void publishesItsPublicKeyAsAJwkSet() {
        @SuppressWarnings("unchecked")
        Map<String, Object> key = ((List<Map<String, Object>>) jwt.jwks().get("keys")).get(0);

        assertThat(key).containsEntry("kty", "RSA").containsEntry("alg", "RS256").containsEntry("use", "sig")
                .containsEntry("e", "AQAB");
        assertThat((String) key.get("n")).hasSizeGreaterThan(300).doesNotContain("=");
        String kid = (String) key.get("kid");
        String header = new String(Base64.getUrlDecoder().decode(tokenFor("asha").split("\\.")[0]), StandardCharsets.UTF_8);
        assertThat(header).contains("\"kid\":\"" + kid + "\"").contains("\"alg\":\"RS256\"");
    }

    @Test
    void refusesAMissingOrShortKey() {
        assertThatThrownBy(() -> new JwtUtils("", 60_000)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtUtils("-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----", 60_000))
                .isInstanceOf(IllegalStateException.class);
    }
}
