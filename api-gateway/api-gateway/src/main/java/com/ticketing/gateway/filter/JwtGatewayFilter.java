package com.ticketing.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    // Identity headers the gateway adds after validating the JWT.
    // Downstream services trust these, so clients must never be able to set them.
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final SecretKey key;

    // The secret can be overridden with the JWT_SECRET environment variable
    // (or jwt.secret property). It must match the Auth Service's value.
    public JwtGatewayFilter(
            @Value("${jwt.secret:TicketingEngineSecretKeyForJwtAuthentication2026Secure}") String secret) {

        this.key = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange originalExchange,
            GatewayFilterChain chain) {

        // Drop any identity headers sent by the client.
        ServerWebExchange exchange = originalExchange.mutate()
                .request(
                        originalExchange.getRequest()
                                .mutate()
                                .headers(headers -> {
                                    headers.remove(USER_ID_HEADER);
                                    headers.remove(USER_EMAIL_HEADER);
                                    headers.remove(USER_ROLE_HEADER);
                                })
                                .build()
                )
                .build();

        String path =
                exchange.getRequest()
                        .getPath()
                        .value();

        // Allow CORS preflight requests
        if (exchange.getRequest()
                .getMethod()
                .name()
                .equals("OPTIONS")) {

            return chain.filter(exchange);
        }

        // Public authentication endpoints
        if (path.equals("/auth/login") ||
                path.equals("/auth/register")) {

            return chain.filter(exchange);
        }

        String authorization =
                exchange.getRequest()
                        .getHeaders()
                        .getFirst("Authorization");

        // JWT required for protected endpoints
        if (authorization == null ||
                !authorization.startsWith("Bearer ")) {

            return unauthorized(exchange, "Authentication required");
        }

        String token = authorization.substring(7);

        Claims claims;

        try {

            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (ExpiredJwtException e) {

            return unauthorized(exchange, "Session expired. Please log in again");

        } catch (Exception e) {

            return unauthorized(exchange, "Invalid token. Please log in again");
        }

        Object userIdClaim = claims.get("userId");

        // Tokens issued before userId was added to the JWT cannot be used.
        if (!(userIdClaim instanceof Number)) {

            return unauthorized(exchange, "Invalid token. Please log in again");
        }

        String userId =
                String.valueOf(((Number) userIdClaim).longValue());

        String email = claims.getSubject();

        Object roleClaim = claims.get("role");
        String role = roleClaim != null ? String.valueOf(roleClaim) : "USER";

        ServerHttpRequest authenticatedRequest =
                exchange.getRequest()
                        .mutate()
                        .headers(headers -> {
                            headers.set(USER_ID_HEADER, userId);
                            headers.set(USER_EMAIL_HEADER, email);
                            headers.set(USER_ROLE_HEADER, role);
                        })
                        .build();

        return chain.filter(
                exchange.mutate()
                        .request(authenticatedRequest)
                        .build()
        );
    }

    private Mono<Void> unauthorized(
            ServerWebExchange exchange,
            String message) {

        ServerHttpResponse response = exchange.getResponse();

        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] body =
                ("{\"message\":\"" + message + "\"}")
                        .getBytes(StandardCharsets.UTF_8);

        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(body))
        );
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
