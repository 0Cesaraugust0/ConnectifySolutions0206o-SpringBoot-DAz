package com.connectify.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/** Consolidates public GET/HEAD pages; payment callbacks and local development keep their host. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CanonicalHostFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC_PATHS = Set.of("/", "/events", "/register", "/login",
            "/help", "/terms", "/privacy", "/cookies", "/sitemap.xml", "/robots.txt");
    private final String baseUrl;
    private final String alternateHost;

    public CanonicalHostFilter(@Value("${seo.base-url:https://www.showyouplay.com}") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        String host = URI.create(this.baseUrl).getHost();
        this.alternateHost = host != null && host.startsWith("www.") ? host.substring(4) : "";
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if ((PUBLIC_PATHS.contains(path) || path.startsWith("/events/"))
                && !alternateHost.isEmpty() && alternateHost.equalsIgnoreCase(request.getServerName())
                && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
            String query = request.getQueryString();
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader("Location", baseUrl + ("/".equals(path) ? "/events" : path)
                    + (query == null || query.isEmpty() ? "" : "?" + query));
            return;
        }
        chain.doFilter(request, response);
    }
}
