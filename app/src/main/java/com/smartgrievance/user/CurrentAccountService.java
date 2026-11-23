package com.smartgrievance.user;

import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class CurrentAccountService {
    private final UserRepository accounts;

    public CurrentAccountService(UserRepository accounts) {
        this.accounts = accounts;
    }

    public Optional<Account> find(long id) { return accounts.byId(id); }
    public boolean lockUnverifiedCitizen(long id) { return accounts.lockUnverifiedCitizen(id); }

    public void markVerified(long id, LocalDateTime now) { accounts.markVerified(id, now); }
}
