package com.googlephotos.server.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlephotos.server.gateway.config.GatewayConfig;
import com.googlephotos.server.gateway.security.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(AuthGlobalFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    // Pattern to match streaming video routes that need query param auth
    private static final String VIDEO_PLAYBACK_PATTERN = "/**/video/v1/playback";

    private final JwtUtil jwtUtil;
    private final GatewayConfig gatewayConfig;
    private final ObjectMapper objectMapper; // For safe JSON serialization
    private final AntPathMatcher pathMatcher = new AntPathMatcher(); // For robust path matching
    @Value("${contact.x_api_key}")
    private String contactsXAPIKey;

    @Autowired
    public AuthGlobalFilter(JwtUtil jwtUtil, GatewayConfig gatewayConfig, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.gatewayConfig = gatewayConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        logger.debug("Processing request for path: {}", path);

        if (isPublicRoute(path)) {
            logger.debug("Bypassing authentication for public route: {}", path);
            return chain.filter(exchange);
        }

        // --- UPDATED TOKEN EXTRACTION ---
        String token = extractToken(request);
        // --- END OF UPDATE ---

        if (token == null) {
            return createErrorResponse(exchange, "Authorization token missing or invalid", HttpStatus.UNAUTHORIZED);
        }

        if (!jwtUtil.validateToken(token)) {
            return createErrorResponse(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
        }

        try {
            String username = jwtUtil.getUsername(token);
            String role = jwtUtil.getRole(token);
            String sessionId = jwtUtil.getSessionId(token);

            if (username == null || username.trim().isEmpty()) {
                return createErrorResponse(exchange, "Token missing required 'username' claim", HttpStatus.UNAUTHORIZED);
            }

            ServerHttpRequest modifiedRequest = request.mutate()
                    .header(SecurityHeaderConstants.USER_ID, username.trim())
                    .header(SecurityHeaderConstants.SESSION_ID, sessionId)
                    .header(SecurityHeaderConstants.USER_ROLE, role != null ? role.trim() : "USER")
                    .header(SecurityHeaderConstants.AUTHENTICATED_FLAG, "true")
                    .header(SecurityHeaderConstants.X_API_KEY, contactsXAPIKey)
                    .build();

            logger.info("Authentication successful for user: {} (role: {}) on path: {}",
                    username, role != null ? role : "USER", path);

            return chain.filter(exchange.mutate().request(modifiedRequest).build());

        } catch (Exception e) {
            logger.error("Authentication processing failed for path '{}'", path, e);
            return createErrorResponse(exchange, "Failed to process authentication claims", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Tries to extract the token from the Authorization header first,
     * then checks the "token" query parameter as a fallback for specific routes.
     */
    private String extractToken(ServerHttpRequest request) {
        // 1. Try Authorization header (standard method)
        String token = extractTokenFromHeader(request.getHeaders());
        if (token != null) {
            return token;
        }

        // 2. Try "token" query parameter (for video streaming)
        token = extractTokenFromQueryParam(request);
        if (token != null) {
            return token;
        }

        return null;
    }

    /**
     * Extracts token from the Authorization header.
     */
    private String extractTokenFromHeader(HttpHeaders headers) {
        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.trim().startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authHeader.trim().substring(BEARER_PREFIX_LENGTH);
    }

    /**
     * Extracts token from the "token" query parameter,
     * ONLY for routes matching the video playback pattern.
     */
    private String extractTokenFromQueryParam(ServerHttpRequest request) {
        String path = request.getPath().toString();

        // Security: ONLY check query params for the designated video route
        if (!pathMatcher.match(VIDEO_PLAYBACK_PATTERN, path)) {
            return null;
        }

        String tokenParam = request.getQueryParams().getFirst("token");
        if (tokenParam == null || tokenParam.trim().isEmpty()) {
            return null;
        }

        logger.warn("Using query parameter token for video playback on path: {}. This should only be for media streaming.", path);

        // Handle if user pasted "Bearer ey..." or just "ey..."
        String cleanToken = tokenParam.trim();
        if (cleanToken.startsWith(BEARER_PREFIX)) {
            return cleanToken.substring(BEARER_PREFIX_LENGTH);
        }

        // Assume raw token
        return cleanToken;
    }

    private Mono<Void> createErrorResponse(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> errorDetails = Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "path", exchange.getRequest().getPath().toString()
        );

        try {
            byte[] responseBytes = objectMapper.writeValueAsBytes(errorDetails);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(responseBytes)));
        } catch (JsonProcessingException e) {
            logger.error("Error writing JSON error response", e);
            // Fallback to a plain text response if JSON serialization fails
            return response.writeWith(Mono.just(response.bufferFactory().wrap(message.getBytes())));
        }
    }

    private boolean isPublicRoute(String path) {
        return gatewayConfig.getPublicRoutes().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    public int getOrder() {
        // Runs after CORS filter but before any routing logic.
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}