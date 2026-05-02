package com.candleservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);

    @Value("${API_KEY_1:dev-key-abc123}")
    private String apiKey1;

    @Value("${API_KEY_2:}")
    private String apiKey2;

    private final RateLimiterService rateLimiter;

    public ApiKeyAuthFilter(RateLimiterService rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    private List<String> getValidKeys() {
        return Stream.of(apiKey1, apiKey2)
                .filter(k -> k != null && !k.isBlank())
                .collect(Collectors.toList());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String apiKey = request.getHeader("X-API-Key");

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Missing X-API-Key header path={}", request.getRequestURI());
            writeError(response, HttpStatus.UNAUTHORIZED, "Missing X-API-Key header");
            return;
        }

        if (!getValidKeys().contains(apiKey)) {
            log.warn("Invalid API key path={}", request.getRequestURI());
            writeError(response, HttpStatus.UNAUTHORIZED, "Invalid API key");
            return;
        }

        if (!rateLimiter.isAllowed(apiKey)) {
            writeError(response, HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded — max 100 requests per second");
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response,
                            HttpStatus status,
                            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                String.format("{\"error\": \"%s\"}", message)
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") ||
                path.startsWith("/v3/api-docs") ||
                path.equals("/actuator/health") ||
                path.equals("/actuator/info");
    }
}