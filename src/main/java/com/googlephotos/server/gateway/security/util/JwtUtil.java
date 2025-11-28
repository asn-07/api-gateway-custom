package com.googlephotos.server.gateway.security.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${googlephotos.jwt.secret}")
    private String secret;

    @Value("${googlephotos.jwt.expiration-grace:300}")
    private long expirationGraceSeconds;

    private SecretKey getSigningKey() {
        // Ensure secret is at least 256 bits (32 bytes)
        if (secret.getBytes().length < 32) {
            logger.error("JWT secret must be at least 32 bytes (256 bits) for HS256. Current length: {} bytes",
                    secret.getBytes().length);
            throw new IllegalArgumentException("JWT secret too short");
        }
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public Claims extractClaims(String token) {
        try {
            // Create the parser with signing key
            var parser = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build();

            // Parse the signed claims
            var jwt = parser.parseSignedClaims(token);
            Claims claims = jwt.getPayload();

            // Check expiration with grace period
            Date expiration = claims.getExpiration();
            if (expiration != null && expiration.before(new Date())) {
                // Check if within grace period
                long timeDiff = (new Date().getTime() - expiration.getTime()) / 1000;
                if (timeDiff > expirationGraceSeconds) {
                    logger.warn("Token expired: {}", token.substring(0, Math.min(20, token.length())));
                    throw new JwtException("Token expired");
                } else {
                    logger.debug("Token expired but within grace period: {} seconds", timeDiff);
                }
            }

            // Check if subject (username) is present
            if (claims.getSubject() == null || claims.getSubject().trim().isEmpty()) {
                logger.error("Token missing subject claim");
                throw new JwtException("Invalid token: missing subject");
            }

            return claims;

        } catch (JwtException e) {
            logger.error("JWT validation failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error parsing token: {}", e.getMessage(), e);
            throw new JwtException("Invalid token", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                logger.debug("Token is null or empty");
                return false;
            }

            // Extract claims - this will perform all validations
            extractClaims(token);
            return true;

        } catch (JwtException e) {
            logger.debug("Token validation failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error during token validation: {}", e.getMessage());
            return false;
        }
    }

    public String getUsername(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getSubject();
        } catch (Exception e) {
            logger.error("Failed to extract username from token: {}", e.getMessage());
            return null;
        }
    }

    public String getRole(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.get("role", String.class);
        } catch (Exception e) {
            logger.debug("Failed to extract role from token: {}", e.getMessage());
            return null;
        }
    }


    // The improved utility method
    public String getSessionId(String token) {
        try {
            Claims claims = extractClaims(token);
            // Cast is now done safely inside the method
            return (String) claims.get("sessionId");
        } catch (ClassCastException e) {
            // Specific logging for a type mismatch
            logger.error("The 'sessionId' claim is not a String.", e);
            return null;
        } catch (Exception e) {
            // General logging for other token errors
            logger.error("Failed to extract session id from token: {}", e.getMessage());
            return null;
        }
    }
}