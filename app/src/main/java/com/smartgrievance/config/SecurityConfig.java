package com.smartgrievance.config;

import java.net.InetAddress;
import java.util.List;

import com.smartgrievance.security.AppAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import com.smartgrievance.security.AuthSessionService;
import com.smartgrievance.security.ThrottledAuthenticationProvider;
import com.smartgrievance.security.UserStateFilter;
import com.smartgrievance.security.WebAuthentication;
import com.smartgrievance.user.CurrentAccountService;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
public class SecurityConfig {
    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; "
            + "form-action 'self'; frame-ancestors 'none'";
    private static final String PERMISSIONS =
            "camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()";

    @Bean
    PasswordEncoder passwordEncoder(AppProperties app) {
        return new BCryptPasswordEncoder(app.security().bcryptStrength());
    }

    @Bean
    SecurityContextRepository securityContexts() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    @Bean
    HttpSessionCsrfTokenRepository csrfTokens() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SessionAuthenticationStrategy registrationSessionStrategy(HttpSessionCsrfTokenRepository tokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(tokens)));
    }

    @Bean
    WebSecurityCustomizer firewall() {
        return web -> web.httpFirewall(new StrictHttpFirewall())
                .requestRejectedHandler(new HttpStatusRequestRejectedHandler(400));
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ThrottledAuthenticationProvider provider,
                                    SecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrfTokens,
                                    AuthSessionService sessions, CurrentAccountService accounts) throws Exception {
        var accessDenied = new AppAccessDeniedHandler();
        var cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(request -> {
            String path = WebAuthentication.path(request);
            String accept = request.getHeader("Accept");
            return "GET".equals(request.getMethod()) && !WebAuthentication.notificationCount(request)
                    && !path.equals("/error") && !path.equals("/favicon.ico") && !path.startsWith("/actuator/")
                    && !path.startsWith("/attachments/") && (accept == null || accept.contains("text/html")
                    || accept.equals("*/*"));
        });
        return http.authenticationManager(new ProviderManager(provider))
                .securityContext(context -> context.securityContextRepository(contexts))
                .requestCache(requests -> requests.requestCache(cache))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .addFilterAfter(new UserStateFilter(accounts, sessions), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/webjars/**", "/css/**", "/js/**", "/images/**",
                                "/vendor/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").access((authentication, context) ->
                                new AuthorizationDecision(isLoopback(context.getRequest().getRemoteAddr())
                                        || authentication.get().getAuthorities().stream()
                                                .anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN"))))
                        .requestMatchers("/", "/login", "/register").permitAll()
                        .requestMatchers("/verify-mobile/**", "/citizen/**").hasRole("CITIZEN")
                        .requestMatchers("/officer/**").hasRole("OFFICER")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/complaints/**", "/attachments/**", "/notifications/**", "/account/**")
                                .authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.accessDeniedHandler(accessDenied)
                        .authenticationEntryPoint((request, response, cause) -> {
                            if (WebAuthentication.notificationCount(request)) {
                                WebAuthentication.unauthenticated(response);
                            } else {
                                boolean expired = request.getRequestedSessionId() != null
                                        && !request.isRequestedSessionIdValid();
                                response.sendRedirect(request.getContextPath() + (expired ? "/login?expired" : "/login"));
                            }
                        }))
                .formLogin(form -> form.loginPage("/login").usernameParameter("email")
                        .successHandler(WebAuthentication.successHandler(cache))
                        .failureHandler(WebAuthentication.failureHandler()))
                .logout(logout -> logout.logoutRequestMatcher(request -> {
                            var user = SecurityContextHolder.getContext().getAuthentication();
                            return "POST".equals(request.getMethod()) && WebAuthentication.path(request).equals("/logout")
                                    && user != null && user.isAuthenticated() && !(user instanceof AnonymousAuthenticationToken);
                        }).addLogoutHandler(WebAuthentication::logout)
                        .logoutSuccessUrl("/login?logout").deleteCookies("JSESSIONID"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS))
                        .frameOptions(frame -> frame.deny())
                        .crossOriginOpenerPolicy(policy -> policy.policy(CrossOriginOpenerPolicy.SAME_ORIGIN))
                        .crossOriginResourcePolicy(policy -> policy.policy(CrossOriginResourcePolicy.SAME_ORIGIN)))
                .build();
    }

    private static boolean isLoopback(String remoteAddress) {
        try {
            return InetAddress.ofLiteral(remoteAddress).isLoopbackAddress();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
