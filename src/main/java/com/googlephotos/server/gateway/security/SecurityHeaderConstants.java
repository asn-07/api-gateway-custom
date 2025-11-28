package com.googlephotos.server.gateway.security;
/**
 * A utility class to hold constant values for custom security headers.
 * This prevents the use of "magic strings" and reduces the risk of typos.
 */
public final class SecurityHeaderConstants {

    // Private constructor to prevent instantiation
    private SecurityHeaderConstants() {}

    public static final String USER_ID = "X-User-Id";
    public static final String SESSION_ID = "X-Session-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String AUTHENTICATED_FLAG = "X-Authenticated";
    public static final String X_API_KEY = "X-API-Key";
}