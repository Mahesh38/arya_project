package com.smartgrievance.complaint.web;

import com.smartgrievance.workflow.TransitionRules.Action;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkflowCopy {
    private static final String REASON = "Reason";

    public record Copy(String title, String submit, String intro, String label, String help, boolean required,
                       boolean department, boolean files, String style, List<String> fields) {}

    public Copy forAction(Action action) {
        return switch (action) {
            case WITHDRAW -> remark("Withdraw complaint", "Withdrawing cancels your complaint. This cannot be undone.",
                    "Reason for withdrawal", "Why are you withdrawing it? (10–500 characters)", true, "danger");
            case CLOSE -> remark("Close complaint", "Closing confirms that the problem has been fixed. Thank you for checking.",
                    "Feedback (optional)", "Anything you want the department to know (up to 500 characters)", false, "success");
            case REOPEN -> remark("Reopen complaint", "Tell the department what is still wrong.",
                    REASON, "10–500 characters. The department will see this.", true, "primary");
            case START_WORK -> remark("Start work", "Let the citizen know what will happen next.",
                    "Remark", "Team scheduled for tomorrow morning. Visible to the citizen.", true, "primary");
            case ADD_REMARK -> new Copy("Add progress update", "Add update", "Post an update without changing the status.",
                    "Progress update", "Visible to the citizen and watchers.", true, false, false, "primary", List.of("remark"));
            case RESOLVE -> new Copy("Resolve complaint", "Mark as resolved",
                    "Describe what was done. The citizen can then close or reopen the complaint during the review period.",
                    "Resolution remark", "Visible to the citizen.", true, false, true, "success", List.of("remark", "files"));
            case SEND_BACK -> new Copy("Send back to the Grievance Cell", "Send back",
                    "Use this when the complaint belongs to another department. It will leave your queue.",
                    "Remark", "Which department should handle it? If it duplicates a complaint in another department, mention its GRV number.",
                    true, false, false, "secondary", List.of("remark"));
            case REJECT -> remark("Reject complaint", "Rejecting ends the complaint. The citizen will see your reason.",
                    REASON, "Not a public issue: private society premises.", true, "danger");
            case ASSIGN -> department("Assign to a department", "Assign", "The category’s default department is preselected when available.", false);
            case REASSIGN -> department("Reassign to another department", "Reassign", "", true);
            case MARK_DUPLICATE -> new Copy("Mark as duplicate", "Link as duplicate", "", "Note to the citizen",
                    "10–500 characters", true, false, false, "primary", List.of("originalNo", "remark"));
            default -> throw new IllegalArgumentException("No form copy for " + action);
        };
    }

    private static Copy remark(String title, String intro, String label, String help, boolean required, String style) {
        return new Copy(title, title, intro, label, help, required, false, false, style, List.of("remark"));
    }

    private static Copy department(String title, String submit, String intro, boolean required) {
        return new Copy(title, submit, intro, required ? "Remark" : "Remark (optional)",
                required ? "Why is it moving?" : null, required, true, false, "primary", List.of("departmentId", "remark"));
    }
}
