package com.smartgrievance.workflow;

import java.time.LocalDateTime;
import java.util.EnumSet;
import com.smartgrievance.access.ComplaintAccessPolicy;
import com.smartgrievance.common.exception.InvalidTransitionException;
import com.smartgrievance.common.exception.Subject;
import com.smartgrievance.access.ComplaintAccessPolicy.Relation;
import com.smartgrievance.complaint.ComplaintStatus;

import static com.smartgrievance.access.ComplaintAccessPolicy.Relation.*;
import static com.smartgrievance.complaint.ComplaintStatus.*;

public final class TransitionRules {
    private TransitionRules() {}

    public enum Action {
        EDIT, WITHDRAW, CLOSE, REOPEN, UNWATCH, WATCH, START_REVIEW, ASSIGN, REASSIGN,
        START_WORK, ADD_REMARK, RESOLVE, SEND_BACK, REJECT, MARK_DUPLICATE
    }

    public static boolean allows(Action action, ComplaintStatus status, Relation relation) {
        boolean assignedWork = status == ASSIGNED || status == IN_PROGRESS || status == REOPENED;
        boolean startable = status == ASSIGNED || status == REOPENED;
        boolean staff = relation == ADMIN || relation == DEPARTMENT_OFFICER;
        return switch (action) {
            case EDIT -> relation == OWNER && (status == SUBMITTED || status == UNDER_REVIEW);
            case WITHDRAW -> relation == OWNER && (status == SUBMITTED || status == UNDER_REVIEW || status == ASSIGNED);
            case CLOSE, REOPEN -> relation == OWNER && status == RESOLVED;
            case UNWATCH -> relation == WATCHER;
            case WATCH -> relation == LINKED_OWNER;
            case START_REVIEW -> relation == ADMIN && status == SUBMITTED;
            case ASSIGN -> relation == ADMIN && status == UNDER_REVIEW;
            case REASSIGN -> relation == ADMIN && assignedWork;
            case START_WORK -> staff && startable;
            case ADD_REMARK, RESOLVE -> staff && status == IN_PROGRESS;
            case SEND_BACK -> relation == DEPARTMENT_OFFICER && startable;
            case REJECT, MARK_DUPLICATE -> (staff && assignedWork)
                    || (relation == ADMIN && (status == SUBMITTED || status == UNDER_REVIEW));
        };
    }

    public record Decision(boolean allowed, String refusal, boolean showNotice) {
        static Decision permitted() { return new Decision(true, null, false); }
    }

    public static Decision decide(Action action, ActorContext actor, ComplaintFacts facts, LocalDateTime now,
                                  int reviewWindowDays, int maxReopens) {
        var relation = ComplaintAccessPolicy.relation(actor, facts.ownerId(), facts.departmentId(),
                facts.viewerIsActiveWatcher(), facts.viewerOwnsLinkedDuplicate());
        if (action == Action.UNWATCH && relation == LINKED_OWNER && facts.viewerIsActiveWatcher()) {
            relation = WATCHER;
        }
        if (!allows(action, facts.status(), relation)) {
            if (relation != OWNER && (action == Action.EDIT || action == Action.WITHDRAW
                    || action == Action.CLOSE || action == Action.REOPEN)) {
                return new Decision(false, "error.owner.only", false);
            }
            if (action == Action.EDIT) return new Decision(false, "error.edit.notAllowed", false);
            if (action == Action.WITHDRAW && workStarted(facts.status())) {
                return new Decision(false, "error.withdraw.workStarted", false);
            }
            if (action == Action.UNWATCH) return new Decision(false, "error.watch.notWatching", false);
            if (action == Action.WATCH && relation == OWNER) {
                return new Decision(false, "error.watch.ownComplaint", false);
            }
            return new Decision(false, "error.state.notAllowed", false);
        }
        if (action == Action.WITHDRAW && facts.hasLinkedDuplicates()) {
            return new Decision(false, "error.withdraw.linkedDuplicates", true);
        }
        if (action == Action.WATCH && facts.viewerIsActiveWatcher()) {
            return new Decision(false, "error.watch.alreadyWatching", false);
        }
        if ((action == Action.CLOSE || action == Action.REOPEN)
                && !withinReviewWindow(now, facts.resolvedAt(), reviewWindowDays)) {
            return new Decision(false, "error.review.ended", true);
        }
        if (action == Action.REOPEN && facts.reopenCount() >= maxReopens) {
            return new Decision(false, "error.reopen.limit", true);
        }
        return Decision.permitted();
    }

    public static EnumSet<Action> allowedActions(ActorContext actor, ComplaintFacts facts, LocalDateTime now,
                                                  int reviewWindowDays, int maxReopens) {
        var actions = EnumSet.noneOf(Action.class);
        for (Action action : Action.values()) {
            if (decide(action, actor, facts, now, reviewWindowDays, maxReopens).allowed()) actions.add(action);
        }
        return actions;
    }

    public static void require(Action action, ActorContext actor, ComplaintFacts facts, LocalDateTime now,
                               int reviewWindowDays, int maxReopens, Subject subject) {
        var decision = decide(action, actor, facts, now, reviewWindowDays, maxReopens);
        if (!decision.allowed()) {
            throw new InvalidTransitionException(decision.refusal(), subject, facts.status().label());
        }
    }

    public static boolean workStarted(ComplaintStatus status) {
        return status == IN_PROGRESS || status == RESOLVED || status == REOPENED;
    }

    public static boolean withinReviewWindow(LocalDateTime now, LocalDateTime resolvedAt, int reviewWindowDays) {
        return resolvedAt != null && !now.isAfter(resolvedAt.plusDays(reviewWindowDays));
    }

    public static boolean canAutoClose(ComplaintStatus status, LocalDateTime now, LocalDateTime resolvedAt, int days) {
        return status == RESOLVED && resolvedAt != null && now.isAfter(resolvedAt.plusDays(days));
    }
}
