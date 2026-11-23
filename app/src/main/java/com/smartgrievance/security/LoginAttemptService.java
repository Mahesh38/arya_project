package com.smartgrievance.security;

import java.time.Clock;
import java.time.Duration;

import com.smartgrievance.config.AppProperties;
import com.smartgrievance.user.AppUserDetailsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {
    private final RateLimiter accounts;
    private final RateLimiter networks;
    private final RateLimiter registrations;
    private final AppProperties.Security config;

    public LoginAttemptService(Clock clock, AppProperties props) {
        accounts = new RateLimiter(clock);
        networks = new RateLimiter(clock);
        registrations = new RateLimiter(clock);
        config = props.security();
    }

    boolean locked(String email, String ip) {
        return accounts.locked(email) || networks.locked(ip);
    }

    void failed(String email, String ip) {
        var login = config.login();
        var account = accounts.record(email, login.maxFailuresPerAccount(), login.window(), login.lock());
        if (account.lockedUntil() != null) {
            SecurityEvents.LOG.warn("LOCKOUT key=email value={} until={}",
                    SecurityEvents.maskEmail(email), account.lockedUntil());
        }
        var network = networks.record(ip, login.maxFailuresPerIp(), login.window(), login.lock());
        if (network.lockedUntil() != null) {
            SecurityEvents.LOG.warn("LOCKOUT key=ip value={} until={}", SecurityEvents.maskIp(ip), network.lockedUntil());
        }
    }

    public void clearAccount(String email) { accounts.clear(AppUserDetailsService.normalizeEmail(email)); }

    public boolean allowRegistration(String ip) {
        var hour = Duration.ofHours(1);
        return registrations.record(ip, config.register().maxPerIpPerHour(), hour, Duration.ZERO).admitted();
    }

    @Scheduled(fixedDelay = 900000)
    void cleanup() {
        accounts.cleanup();
        networks.cleanup();
        registrations.cleanup();
    }
}
