package com.kiki.video.api.auth.jwt;

import com.kiki.video.api.user.model.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final byte[] secret;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes for HS256");
        }
        this.secret = secretBytes;
    }

    public String createAccessToken(User user) {
        return createAccessToken(user, properties.getAccessTokenTtl());
    }

    public String createAccessToken(User user, Duration ttl) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(user.getId()))
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to sign access token", ex);
        }
        return jwt.serialize();
    }

    public JwtPayload parse(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                throw new InvalidAccessTokenException("Invalid access token");
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
                throw new InvalidAccessTokenException("Access token has expired");
            }

            Long userId = claims.getLongClaim("userId");
            String role = claims.getStringClaim("role");
            if (userId == null || role == null || role.isBlank()) {
                throw new InvalidAccessTokenException("Access token is missing required claims");
            }
            return new JwtPayload(userId, role);
        } catch (ParseException | JOSEException ex) {
            throw new InvalidAccessTokenException("Invalid access token");
        }
    }

    public long expiresInSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }
}
