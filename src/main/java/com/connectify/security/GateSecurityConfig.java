package com.connectify.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/** Dedicated gate flow: default CSRF protection stays enabled on every unsafe request. */
@Configuration
public class GateSecurityConfig {
    @Bean
    @Order(1)
    public SecurityFilterChain gateAndEventSecurityChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
        http.securityMatcher("/tickets", "/tickets/validate", "/tickets/validate/**", "/tickets/gate",
                        "/admin/events/**", "/dashboard/admin/events/**")
                .csrf(Customizer.withDefaults())
                .sessionManagement(session -> session.maximumSessions(-1)
                        .sessionRegistry(sessionRegistry).expiredUrl("/login?accessUpdated"))
                .authorizeHttpRequests(auth -> auth
                        // Door passwords create an event-only server session, never an account role.
                        .requestMatchers("/tickets", "/tickets/validate", "/tickets/validate/**", "/tickets/gate").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")));
        return http.build();
    }
}
