package com.smartgrievance.seed;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "local"})
@Order(2)
class DevDataSeeder implements ApplicationRunner {
    private final DemoData data;

    private final ComplaintDemoData complaints;

    DevDataSeeder(DemoData data, ComplaintDemoData complaints) {
        this.data = data;
        this.complaints = complaints;
    }

    @Override
    public void run(ApplicationArguments args) {
        data.seedUsers();
        complaints.seed();
    }
}
