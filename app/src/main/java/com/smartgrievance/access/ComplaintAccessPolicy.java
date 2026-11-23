package com.smartgrievance.access;

import com.smartgrievance.workflow.ActorContext;

public final class ComplaintAccessPolicy {
    private ComplaintAccessPolicy() {}

    public enum Relation { OWNER, WATCHER, LINKED_OWNER, DEPARTMENT_OFFICER, ADMIN, OUTSIDER }

    public static Relation relation(ActorContext user, long ownerId, Long departmentId,
                                    boolean watching, boolean ownsLinkedDuplicate) {
        return switch (user.role()) {
            case ADMIN -> Relation.ADMIN;
            case OFFICER -> departmentId != null && departmentId.equals(user.departmentId())
                    ? Relation.DEPARTMENT_OFFICER : Relation.OUTSIDER;
            case CITIZEN -> user.userId() == ownerId ? Relation.OWNER : ownsLinkedDuplicate ? Relation.LINKED_OWNER
                    : watching ? Relation.WATCHER : Relation.OUTSIDER;
        };
    }

    public static boolean canView(Relation relation) {
        return relation != Relation.OUTSIDER;
    }
}
