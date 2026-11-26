package com.smartgrievance.staff;

import com.smartgrievance.user.Role;
import java.time.LocalDateTime;

public record UserRow(long id, String fullName, String email, String mobile, Role role, String departmentName,
                      boolean active, boolean mobileVerified, LocalDateTime createdAt, int version) {}
