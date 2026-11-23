package com.smartgrievance.user;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.smartgrievance.common.exception.FormError;
import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.common.validation.PasswordPolicy;
import com.smartgrievance.common.validation.UniqueKeys;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AccountService(UserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional
    public Account register(String name, String email, String mobile, String password) {
        var errors = new ArrayList<FormError>();
        if (users.byEmail(email).isPresent()) errors.add(new FormError("email", "validation.email.taken"));
        if (users.mobileTaken(mobile)) errors.add(new FormError("mobile", "validation.mobile.taken"));
        if (PasswordPolicy.containsPersonalData(password, email, mobile)) {
            errors.add(new FormError("password", "validation.password.personal"));
        }
        if (!errors.isEmpty()) throw new FormRejectedException(errors);
        try {
            long id = users.insert(name, email, mobile, encoder.encode(password), Role.CITIZEN, null, true, false,
                    LocalDateTime.now(clock));
            return users.byId(id).orElseThrow();
        } catch (DuplicateKeyException ex) {
            throw UniqueKeys.translate(ex);
        }
    }

    @Transactional
    public Account changePassword(long id, String oldPassword, String newPassword) {
        var row = users.byId(id).orElseThrow(() -> reject("currentPassword", "validation.currentPassword.wrong"));
        if (!row.active() || oldPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(oldPassword, row.passwordHash())) {
            throw reject("currentPassword", "validation.currentPassword.wrong");
        }
        if (encoder.matches(newPassword, row.passwordHash())) {
            throw reject("newPassword", "validation.newPassword.sameAsCurrent");
        }
        if (PasswordPolicy.containsPersonalData(newPassword, row.email(), row.mobile())) {
            throw reject("newPassword", "validation.password.personal");
        }
        if (!users.updateHash(id, row.version(), encoder.encode(newPassword), LocalDateTime.now(clock))) {
            throw reject("currentPassword", "validation.currentPassword.wrong");
        }
        return users.byId(id).orElseThrow();
    }

    private static FormRejectedException reject(String field, String key) {
        return new FormRejectedException(List.of(new FormError(field, key)));
    }
}
