package com.smartgrievance.user;

public enum Role {
    CITIZEN("/citizen/complaints", "My Complaints"),
    OFFICER("/officer/queue", "Department Queue"),
    ADMIN("/admin/dashboard", "Dashboard");

    private final String home;
    private final String title;

    Role(String home, String title) {
        this.home = home;
        this.title = title;
    }

    public String home() { return home; }
    public String title() { return title; }
}
