package com.smartgrievance.user;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import com.smartgrievance.common.validation.PasswordPolicy;
import com.smartgrievance.config.AppProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountBootstrap {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AccountBootstrap(UserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional
    public void ensureAdmin(AppProperties.Admin admin, boolean production) {
        if (users.adminExists()) return;
        String email = AppUserDetailsService.normalizeEmail(admin.email());
        String password = admin.password();
        if (production && ("Admin@1234".equals(password) || !PasswordPolicy.isStrong(password)
                || PasswordPolicy.containsPersonalData(password, email, admin.mobile()))) {
            throw new IllegalStateException("Set a strong ADMIN_PASSWORD before creating the first administrator.");
        }
        users.insert(admin.name(), email, admin.mobile(), encoder.encode(password), Role.ADMIN,
                null, true, true, LocalDateTime.now(clock));
    }

    @Transactional
    public void seedDemoUsers(List<DemoUser> demoUsers) {
        String hash = encoder.encode("Demo@1234");
        var now = LocalDateTime.now(clock);
        for (var demo : demoUsers) {
            if (users.byEmail(demo.email()).isPresent() || users.mobileTaken(demo.mobile())) continue;
            Long departmentId = demo.department() == null ? null : users.departmentId(demo.department());
            users.insert(demo.name(), demo.email(), demo.mobile(), hash,
                    departmentId == null ? Role.CITIZEN : Role.OFFICER,
                    departmentId, demo.active(), demo.verified(), now);
        }
    }

    public record DemoUser(String name, String email, String mobile, String department, boolean active, boolean verified) {}
}
