package com.alethicode.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class EnsureApiCsrfCookieFilter extends OncePerRequestFilter {

    private final CookieCsrfTokenRepository csrfTokenRepository;

    public EnsureApiCsrfCookieFilter(CookieCsrfTokenRepository csrfTokenRepository) {
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/") || "/csrf".equals(request.getRequestURI())) {
            CsrfToken csrfToken = this.csrfTokenRepository.loadDeferredToken(request, response).get();
            request.setAttribute(CsrfToken.class.getName(), csrfToken);
            request.setAttribute(csrfToken.getParameterName(), csrfToken);
        }

        filterChain.doFilter(request, response);
    }
}
