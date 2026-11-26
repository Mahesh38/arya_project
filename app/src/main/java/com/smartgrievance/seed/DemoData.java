package com.smartgrievance.seed;

import java.util.List;

import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AccountBootstrap.DemoUser;
import org.springframework.stereotype.Component;

@Component
public class DemoData {
    private final AccountBootstrap accounts;

    public DemoData(AccountBootstrap accounts) {
        this.accounts = accounts;
    }

    public void seedUsers() {
        accounts.seedDemoUsers(List.of(
                new DemoUser("Suresh Patil", "suresh.patil@example.com", "9000000101", "Sanitation", true, true),
                new DemoUser("Anita Deshmukh", "anita.deshmukh@example.com", "9000000201", "Water Supply", true, true),
                new DemoUser("Rohan Kulkarni", "rohan.kulkarni@example.com", "9000000202", "Water Supply", true, true),
                new DemoUser("Vikram Jadhav", "vikram.jadhav@example.com", "9000000301", "Road Maintenance", true, true),
                new DemoUser("Pooja Shinde", "pooja.shinde@example.com", "9000000401", "Drainage", true, true),
                new DemoUser("Amit Pawar", "amit.pawar@example.com", "9000000501", "Electrical", true, true),
                new DemoUser("Kiran More", "kiran.more@example.com", "9000000502", "Electrical", false, true),
                new DemoUser("Mahesh Joshi", "mahesh@example.com", "9800000001", null, true, true),
                new DemoUser("Jayesh Gaikwad", "jayesh@example.com", "9800000002", null, true, true),
                new DemoUser("Sneha Kale", "sneha@example.com", "9800000003", null, true, true),
                new DemoUser("Rahul Bhosale", "rahul@example.com", "9800000004", null, true, false)));
    }
}
