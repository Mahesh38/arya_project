package com.smartgrievance.workflow;

import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.user.Role;

public record ActorContext(long userId, Role role, Long departmentId) {
    public static ActorContext of(CurrentUser user) {
        return new ActorContext(user.id(), user.role(), user.departmentId());
    }
}
