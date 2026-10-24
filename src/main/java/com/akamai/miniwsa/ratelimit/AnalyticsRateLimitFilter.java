package com.akamai.miniwsa.ratelimit;

import com.akamai.miniwsa.ingest.ErrorResponse;
import com.akamai.miniwsa.ingest.ValidationError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate-limits analytics read paths (stats + samples) per client IP. Fail-open:
 * if limiting throws, the request proceeds.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AnalyticsRateLimitFilter extends OncePerRequestFilter {

    private final FixedWindowRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public AnalyticsRateLimitFilter(FixedWindowRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/v1/stats/") || path.equals("/v1/events/samples"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            OptionalLong retryAfter = rateLimiter.tryAcquire(clientKey(request));
            if (retryAfter.isPresent()) {
                writeTooManyRequests(response, retryAfter.getAsLong());
                return;
            }
        } catch (RuntimeException ignored) {
            // fail-open
        }
        filterChain.doFilter(request, response);
    }

    private static String clientKey(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(List.of(
                new ValidationError(null, "Rate limit exceeded; retry after " + retryAfterSeconds + "s")));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
