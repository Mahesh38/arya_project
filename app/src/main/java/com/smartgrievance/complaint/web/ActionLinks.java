package com.smartgrievance.complaint.web;

import com.smartgrievance.user.Role;
import com.smartgrievance.workflow.TransitionRules.Action;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpMethod;

public final class ActionLinks {
    private ActionLinks() {}

    public record ActionLink(Action action, String label, String url, HttpMethod method, boolean confirm, String style) {}

    public static List<ActionLink> forComplaint(String number, Role role, Set<Action> actions) {
        String staff = role == Role.ADMIN ? "admin" : "officer";
        return Arrays.stream(Action.values()).filter(actions::contains).map(action -> switch (action) {
            case EDIT -> link(action, "Edit", "citizen", number, "edit", "secondary");
            case WITHDRAW -> link(action, "Withdraw", "citizen", number, "withdraw", "danger");
            case CLOSE -> link(action, "Close complaint", "citizen", number, "close", "success");
            case REOPEN -> link(action, "Reopen", "citizen", number, "reopen", "primary");
            case UNWATCH -> form(action, "Unwatch", "citizen", number, "unwatch", "secondary");
            case WATCH -> form(action, "Watch again", "citizen", number, "watch", "secondary");
            case START_REVIEW -> form(action, "Start review", "admin", number, "review", "primary");
            case ASSIGN -> link(action, "Assign", "admin", number, "assign", "primary");
            case REASSIGN -> link(action, "Reassign", "admin", number, "reassign", "primary");
            case START_WORK -> link(action, "Start work", staff, number, "start", "primary");
            case ADD_REMARK -> link(action, "Add update", staff, number, "remark", "primary");
            case RESOLVE -> link(action, "Mark as resolved", staff, number, "resolve", "success");
            case SEND_BACK -> link(action, "Send back", "officer", number, "send-back", "secondary");
            case REJECT -> link(action, "Reject", staff, number, "reject", "danger");
            case MARK_DUPLICATE -> link(action, "Mark as duplicate", staff, number, "duplicate", "secondary");
        }).toList();
    }

    private static ActionLink link(Action action, String label, String prefix, String number, String segment, String style) {
        return new ActionLink(action, label, url(prefix, number, segment), HttpMethod.GET, false, style);
    }

    private static ActionLink form(Action action, String label, String prefix, String number, String segment, String style) {
        return new ActionLink(action, label, url(prefix, number, segment), HttpMethod.POST, true, style);
    }

    private static String url(String prefix, String number, String segment) {
        return "/" + prefix + "/complaints/" + number + "/" + segment;
    }
}
