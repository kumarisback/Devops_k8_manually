package com.example.demo.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Issues and checks sign-in tokens: JWTs signed with RS256. The private key
 * (app.jwt.signing-key, PKCS#8 PEM from the jwt-signing-key Secret) never
 * leaves this service; the public key is published as a JWK Set
 * ({@link #jwks()}, at /api/auth/jwks.json) for other services to verify
 * tokens with. Every token names its issuer and its key (kid), so a key can
 * be rotated by publishing the new one next to the old.
 */
@Component
public class JwtUtils {

    /** The iss claim; verifiers reject tokens from anyone else. */
    public static final String ISSUER = "user-service";

    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    private final RSAPrivateCrtKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;
    private final long expirationMs;

    public JwtUtils(@Value("${app.jwt.signing-key}") String signingKeyPem,
                    @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.privateKey = parsePrivateKey(signingKeyPem);
        this.publicKey = publicKeyOf(privateKey);
        this.keyId = thumbprint(publicKey);
        this.expirationMs = expirationMs;
    }

    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        Date now = new Date();
        return Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(ISSUER)
                .subject(userPrincipal.getUsername())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * False for any token that isn't ours and current: malformed, expired,
     * signed with another key (JwtException covers them all, including a bad
     * signature), from another issuer, or empty.
     */
    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser().verifyWith(publicKey).requireIssuer(ISSUER).build().parseSignedClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.warn("jwt_rejected {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /** The public key as a JWK Set (RFC 7517), what /api/auth/jwks.json serves. */
    public Map<String, Object> jwks() {
        return Map.of("keys", List.of(Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", keyId,
                "n", base64Url(publicKey.getModulus()),
                "e", base64Url(publicKey.getPublicExponent()))));
    }

    private static RSAPrivateCrtKey parsePrivateKey(String pem) {
        if (pem == null || !pem.contains("-----BEGIN PRIVATE KEY-----")) {
            throw new IllegalStateException("app.jwt.signing-key must be an RSA private key in PKCS#8 PEM (-----BEGIN PRIVATE KEY-----)");
        }
        String body = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        try {
            RSAPrivateCrtKey key = (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
            if (key.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("app.jwt.signing-key must be at least 2048 bits");
            }
            return key;
        } catch (GeneralSecurityException | ClassCastException | IllegalArgumentException e) {
            throw new IllegalStateException("app.jwt.signing-key is not a readable RSA private key", e);
        }
    }

    private static RSAPublicKey publicKeyOf(RSAPrivateCrtKey privateKey) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot derive the public key", e);
        }
    }

    // RFC 7638 JWK thumbprint: SHA-256 of the key's required members in
    // lexicographic order, so the same key always gets the same kid.
    private static String thumbprint(RSAPublicKey key) {
        String canonical = "{\"e\":\"" + base64Url(key.getPublicExponent()) + "\",\"kty\":\"RSA\",\"n\":\"" + base64Url(key.getModulus()) + "\"}";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // Unsigned big-endian bytes, as JWK requires: BigInteger adds a leading
    // zero byte when the top bit is set.
    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
