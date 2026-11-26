package com.smartgrievance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * The H2 web console of the local profile (D131), at {@code /h2-console} on this computer only.
 * <p>
 * It exists only when {@code spring.h2.console.enabled=true}, which only {@code application-local.properties} sets;
 * production refuses that setting (P-8). The console logs in to the database itself (user and password of the local
 * datasource), so this separate filter chain does not ask for an application login. It allows loopback addresses
 * only, as does H2 itself ({@code web-allow-others=false}). The application's strict CSP, frame ban and CSRF tokens
 * would break the console's own frames and scripts, so this chain uses same-origin frames and no CSRF instead; the
 * rest of the application keeps its own chain unchanged.
 */
@Configuration
@ConditionalOnBooleanProperty("spring.h2.console.enabled")
class H2ConsoleSecurityConfig {
    private static final IpAddressMatcher IPV4_LOOPBACK = new IpAddressMatcher("127.0.0.0/8");
    private static final IpAddressMatcher IPV6_LOOPBACK = new IpAddressMatcher("::1");

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain h2ConsoleChain(HttpSecurity http, @Value("${spring.h2.console.path:/h2-console}") String path)
            throws Exception {
        http.securityMatcher(path + "/**", path)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().access((authentication, context) -> {
                    var request = context.getRequest();
                    return new AuthorizationDecision(IPV4_LOOPBACK.matches(request) || IPV6_LOOPBACK.matches(request));
                }))
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frames -> frames.sameOrigin()))
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.none()));
        return http.build();
    }
}
