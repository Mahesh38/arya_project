package com.smartgrievance.workflow;

import com.smartgrievance.access.ComplaintAccessPolicy;
import com.smartgrievance.attachment.AttachmentPurpose;
import com.smartgrievance.attachment.AttachmentService;
import com.smartgrievance.attachment.FileStorageService;
import com.smartgrievance.common.exception.ConcurrentUpdateException;
import com.smartgrievance.common.exception.FormError;
import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.common.exception.InvalidTransitionException;
import com.smartgrievance.common.exception.NotFoundException;
import com.smartgrievance.common.exception.Subject;
import com.smartgrievance.common.web.DisplayFormat;
import com.smartgrievance.complaint.ComplaintRepository;
import com.smartgrievance.complaint.ComplaintRepository.Complaint;
import com.smartgrievance.complaint.ComplaintRepository.Expired;
import com.smartgrievance.complaint.ComplaintStatus;
import com.smartgrievance.complaint.ComplaintSummary;
import com.smartgrievance.complaint.ComplaintVisibility;
import com.smartgrievance.config.AppProperties;
import com.smartgrievance.history.ComplaintHistory;
import com.smartgrievance.history.ComplaintHistory.Event;
import com.smartgrievance.masterdata.LookupOption;
import com.smartgrievance.notification.NotificationService;
import com.smartgrievance.notification.NotificationService.WorkflowNotice;
import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.workflow.TransitionRules.Action;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import static com.smartgrievance.complaint.ComplaintStatus.*;

@Service
public class ComplaintWorkflowService {
    private static final Logger LOG = LoggerFactory.getLogger(ComplaintWorkflowService.class);
    private final ComplaintRepository complaints;
    private final ComplaintVisibility visibility;
    private final ComplaintHistory history;
    private final NotificationService notifications;
    private final AttachmentService attachments;
    private final FileStorageService storage;
    private final AppProperties.Complaint config;
    private final DisplayFormat format;
    private final Clock clock;

    ComplaintWorkflowService(ComplaintRepository complaints, ComplaintVisibility visibility, ComplaintHistory history,
                             NotificationService notifications, AttachmentService attachments, FileStorageService storage,
                             AppProperties properties, DisplayFormat format, Clock clock) {
        this.complaints = complaints;
        this.visibility = visibility;
        this.history = history;
        this.notifications = notifications;
        this.attachments = attachments;
        this.storage = storage;
        this.config = properties.complaint();
        this.format = format;
        this.clock = clock;
    }

    public record ActionPage(ComplaintSummary summary, Long departmentId, Long defaultDepartmentId, int reopensLeft) {}
    public record WorkflowResult(String department, String reviewDeadline) {}

    @Transactional(readOnly = true)
    public ActionPage actionPage(String no, Action action, CurrentUser actor) {
        var complaint = requireAction(no, action, actor, now(), null);
        var deadline = complaint.status() == RESOLVED ? reviewDeadline(complaint.resolvedAt()) : null;
        var summary = new ComplaintSummary(no, complaint.title(), complaint.status(), complaint.categoryName(),
                complaint.areaName(), complaint.departmentName(), complaint.version(), deadline);
        Long proposed = action == Action.ASSIGN ? complaints.defaultDepartment(complaint.categoryId()) : null;
        return new ActionPage(summary, complaint.departmentId(), proposed,
                Math.max(0, config.maxReopens() - complaint.reopenCount()));
    }

    @Transactional
    public WorkflowResult perform(String no, Action action, int version, String remark, Long requestedDepartment,
                                  List<MultipartFile> files, CurrentUser actor) {
        var time = now();
        var complaint = requireAction(no, action, actor, time, version);
        var to = target(action, complaint.status());
        Long department = complaint.departmentId();
        String departmentName = complaint.departmentName();
        if (action == Action.ASSIGN || action == Action.REASSIGN) {
            var selected = department(requestedDepartment, action == Action.REASSIGN ? department : null);
            department = selected.id();
            departmentName = selected.name();
        } else if (action == Action.SEND_BACK) {
            department = null;
        }
        var resolvedAt = switch (action) {
            case RESOLVE -> time;
            case REOPEN -> null;
            default -> complaint.resolvedAt();
        };
        int reopens = complaint.reopenCount() + (action == Action.REOPEN ? 1 : 0);
        if (action == Action.RESOLVE) {
            var errors = storage.validate(files, AttachmentPurpose.RESOLUTION, 0, 0, actor.id());
            if (!errors.isEmpty()) throw new FormRejectedException(errors);
        }
        if (!complaints.updateWorkflow(complaint, version, to, department, resolvedAt, reopens, time)) throw concurrent(no);
        if (action == Action.RESOLVE) attachments.add(complaint.id(), actor.id(), AttachmentPurpose.RESOLUTION, files);
        var event = event(action);
        Long recordedDepartment = action == Action.SEND_BACK ? complaint.departmentId() : department;
        boolean statusEvent = event != Event.REMARK;
        long eventId = history.record(complaint.id(), event, statusEvent ? complaint.status() : null,
                statusEvent ? to : null, actor.id(), recordedDepartment, null, remark, time);
        var deadline = resolvedAt == null ? null : format.date(reviewDeadline(resolvedAt).toLocalDate());
        notifications.workflow(action, new WorkflowNotice(complaint.id(), eventId, complaint.ownerId(), department,
                actor.id(), no, departmentName, remark, deadline, time));
        if (statusEvent) transition(no, complaint.status(), to, actor.id());
        return new WorkflowResult(departmentName, deadline);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean autoClose(Expired expired, LocalDateTime time) {
        if (!complaints.autoClose(expired, time)) return false;
        int days = config.reviewWindowDays();
        long event = history.record(expired.id(), Event.AUTO_CLOSED, RESOLVED, CLOSED, null, expired.departmentId(), null,
                "Auto-closed: no response within " + days + " days", time);
        notifications.autoClosed(new WorkflowNotice(expired.id(), event, expired.ownerId(), expired.departmentId(),
                null, expired.number(), null, null, null, time), days);
        transition(expired.number(), RESOLVED, CLOSED, null);
        return true;
    }

    private Complaint requireAction(String no, Action action, CurrentUser user, LocalDateTime time, Integer expectedVersion) {
        var complaint = complaints.find(no, false).orElseThrow(() -> new NotFoundException("complaint", no, true));
        var actor = ActorContext.of(user);
        var access = visibility.access(actor, complaint.id(), complaint.ownerId(), complaint.departmentId());
        if (!ComplaintAccessPolicy.canView(access.relation())) throw new NotFoundException("complaint", no, true);
        if (expectedVersion != null && complaint.version() != expectedVersion) throw concurrent(no);
        var facts = new ComplaintFacts(complaint.status(), complaint.ownerId(), complaint.departmentId(), complaint.reopenCount(),
                complaint.resolvedAt(), false, access.watching(), access.linked());
        var decision = TransitionRules.decide(action, actor, facts, time, config.reviewWindowDays(), config.maxReopens());
        if (!decision.allowed()) {
            var deadline = reviewDeadline(complaint.resolvedAt());
            String argument = "error.review.ended".equals(decision.refusal()) && deadline != null
                    ? format.date(deadline.toLocalDate()) : complaint.status().label();
            throw new InvalidTransitionException(decision.refusal(), subject(no), argument);
        }
        return complaint;
    }

    private LookupOption department(Long id, Long current) {
        if (id == null) throw fieldError("validation.departmentId.required");
        if (Objects.equals(id, current)) throw fieldError("validation.departmentId.same");
        return complaints.lookup(ComplaintRepository.Lookup.DEPARTMENT, id, true)
                .filter(LookupOption::active)
                .orElseThrow(() -> fieldError("validation.departmentId.inactive"));
    }

    private static FormRejectedException fieldError(String key) {
        return new FormRejectedException(List.of(new FormError("departmentId", key)));
    }

    private static ComplaintStatus target(Action action, ComplaintStatus current) {
        return switch (action) {
            case START_REVIEW, SEND_BACK -> UNDER_REVIEW;
            case ASSIGN, REASSIGN -> ASSIGNED;
            case START_WORK -> IN_PROGRESS;
            case ADD_REMARK -> current;
            case RESOLVE -> RESOLVED;
            case CLOSE -> CLOSED;
            case REOPEN -> REOPENED;
            case REJECT -> REJECTED;
            default -> throw new IllegalArgumentException("Unsupported workflow action: " + action);
        };
    }

    private static Event event(Action action) {
        return switch (action) {
            case REASSIGN -> Event.REASSIGNED;
            case ADD_REMARK -> Event.REMARK;
            default -> Event.STATUS_CHANGED;
        };
    }

    private LocalDateTime reviewDeadline(LocalDateTime resolvedAt) {
        return resolvedAt == null ? null : resolvedAt.plusDays(config.reviewWindowDays());
    }

    private LocalDateTime now() { return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS); }
    private static Subject subject(String no) { return new Subject(Subject.Type.COMPLAINT, no); }
    private static ConcurrentUpdateException concurrent(String no) {
        return new ConcurrentUpdateException("error.concurrent.complaint", subject(no));
    }

    private static void transition(String no, ComplaintStatus from, ComplaintStatus to, Long actor) {
        LOG.info("COMPLAINT_TRANSITION complaintNo={} from={} to={} actorId={}", no, from, to, actor);
    }
}
