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
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.core.userdetails.UserDetailsService;
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
    UserDetailsService foundationUserDetailsService() {
        return new InMemoryUserDetailsManager(List.of());
    }

    @Bean
    WebSecurityCustomizer firewall() {
        return web -> web.httpFirewall(new StrictHttpFirewall())
                .requestRejectedHandler(new HttpStatusRequestRejectedHandler(400));
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        var accessDenied = new AppAccessDeniedHandler();
        return http
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
                .exceptionHandling(errors -> errors.accessDeniedHandler(accessDenied))
                .formLogin(form -> form.loginPage("/login").usernameParameter("email"))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").deleteCookies("JSESSIONID"))
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
