package com.smartgrievance.otp;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.smartgrievance.common.exception.FormError;
import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.config.AppProperties;
import com.smartgrievance.security.SecurityEvents;
import com.smartgrievance.user.CurrentAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
    private final OtpRepository codes;
    private final CurrentAccountService accounts;
    private final OtpHasher hasher;
    private final OtpSender sender;
    private final Clock clock;
    private final AppProperties.Otp config;
    private final OtpCodeGenerator generator;

    OtpService(OtpRepository codes, CurrentAccountService accounts, OtpHasher hasher,
               OtpSender sender, OtpCodeGenerator generator, Clock clock, AppProperties props) {
        this.codes = codes;
        this.accounts = accounts;
        this.hasher = hasher;
        this.sender = sender;
        this.clock = clock;
        this.generator = generator;
        config = props.otp();
    }

    @Transactional
    public OtpStatusView issueOnRegistration(long userId) {
        return status(userId, true, "register");
    }

    @Transactional
    public OtpStatusView status(long userId, boolean issueIfNeeded) {
        return status(userId, issueIfNeeded, "page");
    }

    private OtpStatusView status(long userId, boolean issueIfNeeded, String trigger) {
        if (!accounts.lockUnverifiedCitizen(userId)) return null;
        var now = now();
        var account = accounts.find(userId).orElseThrow();
        var status = snapshot(userId, account.mobile(), now);
        if (issueIfNeeded && status.attemptsLeft() == 0 && status.resendAllowed()) {
            return issue(userId, account.mobile(), now, trigger);
        }
        return status;
    }

    @Transactional
    public OtpStatusView resend(long userId) {
        if (!accounts.lockUnverifiedCitizen(userId)) return null;
        var now = now();
        var account = accounts.find(userId).orElseThrow();
        var status = snapshot(userId, account.mobile(), now);
        if (!status.resendAllowed()) {
            SecurityEvents.LOG.warn("OTP_LIMIT userId={} reason={}", userId,
                    status.hourlyLimitReached() ? "hourly" : "cooldown");
            if (status.hourlyLimitReached()) {
                throw error(null, "error.otp.hourlyLimit", TIME.format(status.resendAllowedAt()));
            }
            throw error(null, "error.otp.cooldown", ChronoUnit.SECONDS.between(now, status.resendAllowedAt()));
        }
        return issue(userId, account.mobile(), now, "resend");
    }

    @Transactional(noRollbackFor = FormRejectedException.class)
    public boolean verify(long userId, String candidate) {
        if (!accounts.lockUnverifiedCitizen(userId)) return false;
        var now = now();
        var code = codes.recent(userId, cutoff(now)).stream().findFirst().orElse(null);
        if (code == null || !codes.reserve(code.id(), config.maxAttempts(), now)) {
            SecurityEvents.LOG.warn("OTP_FAILED userId={} attemptsLeft=0", userId);
            throw error(null, "validation.otp.expired");
        }
        if (!hasher.matches(userId, candidate, code.hash())) {
            int left = config.maxAttempts() - code.attempts() - 1;
            SecurityEvents.LOG.warn("OTP_FAILED userId={} attemptsLeft={}", userId, left);
            if (left == 0) {
                SecurityEvents.LOG.warn("OTP_LIMIT userId={} reason=exhausted", userId);
                throw error(null, "validation.otp.expired");
            }
            throw error("code", "validation.code.incorrect", left);
        }
        codes.consume(code.id());
        accounts.markVerified(userId, now);
        SecurityEvents.LOG.info("OTP_VERIFIED userId={}", userId);
        return true;
    }

    private OtpStatusView issue(long userId, String mobile, LocalDateTime now, String trigger) {
        String plain = generator.next();
        var expires = now.plus(config.ttl());
        codes.issue(userId, hasher.hash(userId, plain), now, expires);
        var status = snapshot(userId, mobile, now);
        SecurityEvents.LOG.info("OTP_ISSUED userId={} mobile={} trigger={}", userId, status.maskedMobile(), trigger);
        return new OtpStatusView(status.maskedMobile(), now, expires, config.maxAttempts(), status.resendAllowedAt(),
                false, status.hourlyLimitReached(), sender.send(userId, mobile, plain));
    }

    private OtpStatusView snapshot(long userId, String mobile, LocalDateTime now) {
        var recent = codes.recent(userId, cutoff(now));
        var latest = recent.stream().findFirst().orElse(null);
        boolean hourly = recent.size() >= config.maxSendsPerHour();
        var available = latest == null ? now : latest.sentAt().plus(config.resendCooldown());
        if (hourly) {
            var hourlyAvailable = recent.get(config.maxSendsPerHour() - 1).sentAt().plusHours(1);
            if (hourlyAvailable.isAfter(available)) available = hourlyAvailable;
        }
        int attempts = latest == null || latest.used() || !latest.expiresAt().isAfter(now)
                ? 0 : Math.max(0, config.maxAttempts() - latest.attempts());
        return new OtpStatusView(DemoOtpSender.mask(mobile), latest == null ? null : latest.sentAt(),
                latest == null ? null : latest.expiresAt(), attempts, available, !available.isAfter(now), hourly, null);
    }

    private LocalDateTime now() { return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS); }

    private LocalDateTime cutoff(LocalDateTime now) {
        return now.minusHours(1).plusSeconds(1);
    }

    private static FormRejectedException error(String field, String key, Object... args) {
        return new FormRejectedException(List.of(new FormError(field, key, args)));
    }
}
