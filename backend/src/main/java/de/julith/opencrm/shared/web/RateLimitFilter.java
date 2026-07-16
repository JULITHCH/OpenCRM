package de.julith.opencrm.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * In-Memory-Rate-Limiting je Instanz (E-39, docs/10 Abschnitt 8): Token-Bucket je JWT-Subject.
 * Bewusst ohne verteilten Zaehler in Phase 1 — konservative Limits rechnen die Replikazahl ein.
 * Kein Redis. Antwort bei Ueberschreitung: 429 mit X-RateLimit-* und Retry-After.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private final int capacity;
    private final long refillPerSecond;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${opencrm.rate-limit.requests-per-minute:600}") int perMinute,
                           ObjectMapper objectMapper) {
        this.capacity = perMinute;
        this.refillPerSecond = Math.max(1, perMinute / 60);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = subject();
        if (key == null) {
            chain.doFilter(request, response);
            return;
        }
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity));
        long remaining = bucket.tryConsume(refillPerSecond, capacity);
        response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));
        if (remaining < 0) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("Retry-After", "1");
            objectMapper.writeValue(response.getWriter(),
                    ApiProblem.of(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Zu viele Anfragen"));
            return;
        }
        chain.doFilter(request, response);
    }

    private static String subject() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwt ? jwt.getToken().getSubject() : null;
    }

    /** Monoton per nanoTime; keine Wall-Clock, damit Zeitspruenge den Bucket nicht verfaelschen. */
    private static final class Bucket {
        private final AtomicLong tokens;
        private volatile long lastRefillNanos = System.nanoTime();

        Bucket(int initial) {
            this.tokens = new AtomicLong(initial);
        }

        synchronized long tryConsume(long refillPerSecond, int capacity) {
            long now = System.nanoTime();
            long elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000L;
            if (elapsedSeconds > 0) {
                long refill = elapsedSeconds * refillPerSecond;
                tokens.set(Math.min(capacity, tokens.get() + refill));
                lastRefillNanos = now;
            }
            long current = tokens.get();
            if (current <= 0) {
                return -1;
            }
            tokens.decrementAndGet();
            return current - 1;
        }
    }
}
