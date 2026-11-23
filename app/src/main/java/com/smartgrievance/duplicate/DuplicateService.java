package com.smartgrievance.duplicate;

import com.smartgrievance.access.ComplaintAccessPolicy;
import com.smartgrievance.access.ComplaintAccessPolicy.Relation;
import com.smartgrievance.common.exception.BusinessRuleException;
import com.smartgrievance.common.exception.ConcurrentUpdateException;
import com.smartgrievance.common.exception.InvalidTransitionException;
import com.smartgrievance.common.exception.NotFoundException;
import com.smartgrievance.common.exception.Subject;
import com.smartgrievance.complaint.ComplaintRepository;
import com.smartgrievance.complaint.ComplaintRepository.Complaint;
import com.smartgrievance.complaint.ComplaintStatus;
import com.smartgrievance.complaint.ComplaintSummary;
import com.smartgrievance.history.ComplaintHistory;
import com.smartgrievance.history.ComplaintHistory.Event;
import com.smartgrievance.notification.NotificationService;
import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.user.Role;
import com.smartgrievance.watch.WatcherRepository;
import com.smartgrievance.workflow.ActorContext;
import com.smartgrievance.workflow.TransitionRules;
import com.smartgrievance.workflow.TransitionRules.Action;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DuplicateService {
    private static final Logger LOG = LoggerFactory.getLogger(DuplicateService.class);
    private static final Set<ComplaintStatus> ORIGINAL_STATES = Set.of(
            ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS, ComplaintStatus.REOPENED);
    private final ComplaintRepository complaints;
    private final WatcherRepository watchers;
    private final ComplaintHistory history;
    private final NotificationService notifications;
    private final Clock clock;

    DuplicateService(ComplaintRepository complaints, WatcherRepository watchers, ComplaintHistory history,
                     NotificationService notifications, Clock clock) {
        this.complaints = complaints;
        this.watchers = watchers;
        this.history = history;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ComplaintSummary actionPage(String number, CurrentUser actor) {
        return summary(source(number, actor, null));
    }

    @Transactional(readOnly = true)
    public ComplaintSummary preview(String number, String originalNumber, CurrentUser actor) {
        var duplicate = source(number, actor, null);
        return summary(original(duplicate, originalNumber, actor));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void markDuplicate(String number, String originalNumber, int version, String note, CurrentUser actor) {
        source(number, actor, version);
        // A stable order serializes opposite-direction links without a lock-order cycle.
        if (number.compareTo(originalNumber) < 0) {
            complaints.find(number, true);
            complaints.find(originalNumber, true);
        } else {
            complaints.find(originalNumber, true);
            if (!number.equals(originalNumber)) complaints.find(number, true);
        }
        var duplicate = source(number, actor, version);
        var original = original(duplicate, originalNumber, actor);
        var time = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        if (!complaints.linkDuplicate(duplicate, original.id(), version, time)) throw concurrent(number);
        var inherited = watchers.activeWatchers(duplicate.id());
        var recipients = new LinkedHashSet<>(inherited);
        recipients.add(duplicate.ownerId());
        recipients.remove(original.ownerId());
        for (long watcher : recipients) watchers.activateOrInsert(original.id(), watcher, time);
        long event = history.record(duplicate.id(), Event.DUPLICATE_LINKED, duplicate.status(), ComplaintStatus.DUPLICATE,
                actor.id(), duplicate.departmentId(), original.id(), note, time);
        history.record(original.id(), Event.DUPLICATE_RECEIVED, null, null, actor.id(), original.departmentId(),
                duplicate.id(), null, time);
        notifications.duplicateLinked(duplicate.id(), event, duplicate.ownerId(), inherited, number, originalNumber, time);
        LOG.info("COMPLAINT_TRANSITION complaintNo={} from={} to=DUPLICATE actorId={}", number, duplicate.status(), actor.id());
    }

    private Complaint source(String number, CurrentUser actor, Integer version) {
        var complaint = complaints.find(number, false).orElseThrow(() -> missing(number));
        var relation = ComplaintAccessPolicy.relation(ActorContext.of(actor), complaint.ownerId(), complaint.departmentId(), false, false);
        if (relation != Relation.ADMIN && relation != Relation.DEPARTMENT_OFFICER) throw missing(number);
        if (version != null && complaint.version() != version) throw concurrent(number);
        if (!TransitionRules.allows(Action.MARK_DUPLICATE, complaint.status(), relation)) {
            throw new InvalidTransitionException("error.state.notAllowed", subject(number), complaint.status().label());
        }
        return complaint;
    }

    private Complaint original(Complaint duplicate, String number, CurrentUser actor) {
        String source = duplicate.number();
        if (source.equals(number)) throw fault("BR-40", "error.duplicate.self", source);
        var original = complaints.find(number, false)
                .orElseThrow(() -> fault("BR-40", "error.duplicate.notFound", source, number));
        if (actor.role() == Role.OFFICER && !Objects.equals(actor.departmentId(), original.departmentId())) {
            throw fault("BR-43", "error.duplicate.otherDepartment", source, number);
        }
        if (original.status() == ComplaintStatus.DUPLICATE) {
            throw fault("BR-42", "error.duplicate.isDuplicate", source, number, original.originalNo());
        }
        if (!ORIGINAL_STATES.contains(original.status())) {
            throw fault("BR-41", "error.duplicate.notActive", source, number, original.status().label());
        }
        if (complaints.hasDuplicates(duplicate.id())) {
            throw fault("BR-42", "error.state.notAllowed", source, duplicate.status().label());
        }
        return original;
    }

    private static ComplaintSummary summary(Complaint complaint) {
        return new ComplaintSummary(complaint.number(), complaint.title(), complaint.status(), complaint.categoryName(),
                complaint.areaName(), complaint.departmentName(), complaint.version(), null);
    }

    private static Subject subject(String number) { return new Subject(Subject.Type.COMPLAINT, number); }
    private static NotFoundException missing(String number) { return new NotFoundException("complaint", number, true); }

    private static ConcurrentUpdateException concurrent(String number) {
        return new ConcurrentUpdateException("error.concurrent.complaint", subject(number));
    }

    private static BusinessRuleException fault(String rule, String key, String number, Object... arguments) {
        return new BusinessRuleException(rule, key, subject(number), arguments);
    }
}
