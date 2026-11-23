package com.smartgrievance.seed;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@Order(2)
class DevDataSeeder implements ApplicationRunner {
    private final DemoData data;

    DevDataSeeder(DemoData data) { this.data = data; }

    @Override
    public void run(ApplicationArguments args) { data.seedUsers(); }
}
