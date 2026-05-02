package com.alethicode.middleware;

import com.alethicode.config.InternalServiceKeyMatcher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security filter that enforces X-Internal-Service-Key on all /internal/** requests.
 * Defense-in-depth: even if a controller endpoint forgets to call validateServiceKey(),
 * this filter ensures unauthenticated requests are rejected at the filter chain level.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalApiKeyFilter.class);
    private static final String HEADER = "X-Internal-Service-Key";

    private final InternalServiceKeyMatcher keyMatcher;

    public InternalApiKeyFilter(InternalServiceKeyMatcher keyMatcher) {
        this.keyMatcher = keyMatcher;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!keyMatcher.isConfigured()) {
            log.error("Internal service key not configured; rejecting /internal/ request from {}", request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Internal service key not configured");
            return;
        }

        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank() || !keyMatcher.matches(key)) {
            log.warn("Invalid or missing internal service key from {} for {}", request.getRemoteAddr(), request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal service key");
            return;
        }

        chain.doFilter(request, response);
    }
}
