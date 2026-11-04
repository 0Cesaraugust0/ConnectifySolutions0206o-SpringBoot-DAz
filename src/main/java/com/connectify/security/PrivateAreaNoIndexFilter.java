package com.connectify.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class PrivateAreaNoIndexFilter extends OncePerRequestFilter {

    private static final List<String> NO_INDEX_PATHS = List.of(
            "/login", "/register", "/dashboard", "/admin", "/communications",
            "/cart", "/payments", "/tickets", "/setup", "/prototype"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (NO_INDEX_PATHS.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"))) {
            response.setHeader("X-Robots-Tag", "noindex, nofollow");
        }
        filterChain.doFilter(request, response);
    }
}
