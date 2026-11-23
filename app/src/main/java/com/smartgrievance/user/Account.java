package com.smartgrievance.user;

public record Account(long id, String fullName, String email, String mobile, String passwordHash,
                      Role role, Long departmentId, String departmentName, boolean active,
                      boolean mobileVerified, int version) {
    @Override
    public String toString() { return "Account[id=" + id + ", role=" + role + "]"; }
}
