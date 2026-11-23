package com.smartgrievance.watch;

import com.smartgrievance.access.ComplaintAccessPolicy;
import com.smartgrievance.common.exception.BusinessRuleException;
import com.smartgrievance.common.exception.NotFoundException;
import com.smartgrievance.common.exception.Subject;
import com.smartgrievance.complaint.ComplaintRepository;
import com.smartgrievance.complaint.ComplaintVisibility;
import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.user.Role;
import com.smartgrievance.workflow.ActorContext;
import com.smartgrievance.workflow.ComplaintFacts;
import com.smartgrievance.workflow.TransitionRules;
import com.smartgrievance.workflow.TransitionRules.Action;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchService {
    private final ComplaintRepository complaints;
    private final ComplaintVisibility visibility;
    private final WatcherRepository watchers;
    private final Clock clock;

    WatchService(ComplaintRepository complaints, ComplaintVisibility visibility, WatcherRepository watchers, Clock clock) {
        this.complaints = complaints;
        this.visibility = visibility;
        this.watchers = watchers;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean unwatch(String number, CurrentUser user) {
        return change(number, user, Action.UNWATCH);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void watch(String number, CurrentUser user) {
        change(number, user, Action.WATCH);
    }

    private boolean change(String number, CurrentUser user, Action action) {
        if (user.role() != Role.CITIZEN) throw new NotFoundException("complaint", number, true);
        var complaint = complaints.find(number, true)
                .orElseThrow(() -> new NotFoundException("complaint", number, true));
        var actor = ActorContext.of(user);
        var access = visibility.access(actor, complaint.id(), complaint.ownerId(), complaint.departmentId());
        if (!ComplaintAccessPolicy.canView(access.relation())) throw new NotFoundException("complaint", number, true);
        var subject = new Subject(Subject.Type.COMPLAINT, number);
        if (complaint.ownerId() == user.id()) throw new BusinessRuleException("BR-52", "error.watch.ownComplaint", subject);
        if (action == Action.WATCH && access.watching()) {
            throw new BusinessRuleException("BR-52", "error.watch.alreadyWatching", subject);
        }
        var now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        var facts = new ComplaintFacts(complaint.status(), complaint.ownerId(), complaint.departmentId(), complaint.reopenCount(),
                complaint.resolvedAt(), false, access.watching(), access.linked());
        TransitionRules.require(action, actor, facts, now, 0, 0, subject);
        if (action == Action.WATCH) watchers.activateOrInsert(complaint.id(), user.id(), now);
        else watchers.deactivate(complaint.id(), user.id(), now);
        return access.linked();
    }
}
