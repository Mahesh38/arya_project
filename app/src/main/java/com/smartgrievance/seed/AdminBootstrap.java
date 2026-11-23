package com.smartgrievance.seed;

import com.smartgrievance.config.AppProperties;
import com.smartgrievance.user.AccountBootstrap;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@Order(1)
class AdminBootstrap implements ApplicationRunner {
    private final AccountBootstrap accounts;
    private final AppProperties properties;
    private final Environment environment;

    AdminBootstrap(AccountBootstrap accounts, AppProperties properties, Environment environment) {
        this.accounts = accounts;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        accounts.ensureAdmin(properties.admin(), environment.acceptsProfiles(Profiles.of("prod")));
    }
}
