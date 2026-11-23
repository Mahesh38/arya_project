package com.smartgrievance.complaint;

import com.smartgrievance.access.ComplaintAccessPolicy;
import com.smartgrievance.access.ComplaintAccessPolicy.Relation;
import com.smartgrievance.attachment.AttachmentPurpose;
import com.smartgrievance.attachment.AttachmentService;
import com.smartgrievance.common.PageResult;
import com.smartgrievance.common.exception.NotFoundException;
import com.smartgrievance.common.web.DisplayFormat;
import com.smartgrievance.config.AppProperties;
import com.smartgrievance.history.TimelineService;
import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.user.Role;
import com.smartgrievance.workflow.ActorContext;
import com.smartgrievance.workflow.ComplaintFacts;
import com.smartgrievance.workflow.TransitionRules;
import com.smartgrievance.workflow.TransitionRules.Action;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ComplaintQueryService {
    private final ComplaintRepository complaints;
    private final ComplaintTrackingRepository tracking;
    private final ComplaintVisibility visibility;
    private final AttachmentService attachments;
    private final TimelineService timeline;
    private final AppProperties.Complaint config;
    private final Clock clock;
    private final DisplayFormat format;
    private final MessageSource messages;

    ComplaintQueryService(ComplaintRepository complaints, ComplaintTrackingRepository tracking,
            ComplaintVisibility visibility, AttachmentService attachments, TimelineService timeline, AppProperties properties,
            Clock clock, DisplayFormat format, MessageSource messages) {
        this.complaints = complaints;
        this.tracking = tracking;
        this.visibility = visibility;
        this.attachments = attachments;
        this.timeline = timeline;
        this.config = properties.complaint();
        this.clock = clock;
        this.format = format;
        this.messages = messages;
    }

    public PageResult<ComplaintListItem> myComplaints(CurrentUser user, CitizenListFilter filter) {
        return tracking.list(user.id(), filter, false, config.reviewWindowDays());
    }

    public PageResult<ComplaintListItem> watching(CurrentUser user, CitizenListFilter filter) {
        return tracking.list(user.id(), filter, true, config.reviewWindowDays());
    }

    public ComplaintDetailView detail(String no, CurrentUser user) {
        var complaint = complaints.find(no, false).orElseThrow(() -> missing(no));
        var actor = ActorContext.of(user);
        var access = visibility.access(actor, complaint.id(), complaint.ownerId(), complaint.departmentId());
        if (!ComplaintAccessPolicy.canView(access.relation())) throw missing(no);
        boolean staff = user.role() != Role.CITIZEN;
        boolean admin = access.relation() == Relation.ADMIN;
        boolean owner = access.relation() == Relation.OWNER;
        int count = staff ? complaints.duplicateCount(complaint.id()) : 0;
        boolean hasDuplicates = TransitionRules.allows(Action.WITHDRAW, complaint.status(), access.relation())
                && complaints.hasDuplicates(complaint.id());
        var facts = new ComplaintFacts(complaint.status(), complaint.ownerId(), complaint.departmentId(),
                complaint.reopenCount(), complaint.resolvedAt(), hasDuplicates, access.watching(), access.linked());
        var now = LocalDateTime.now(clock);
        var deadline = complaint.status() == ComplaintStatus.RESOLVED && complaint.resolvedAt() != null
                ? complaint.resolvedAt().plusDays(config.reviewWindowDays()) : null;
        var due = staff ? complaint.createdAt().plusDays(complaint.slaDays()) : null;
        boolean overdue = due != null && due.isBefore(now) && complaint.status() != ComplaintStatus.RESOLVED
                && !complaint.status().isFinal();
        var original = complaint.originalNo() == null ? null : new ComplaintDetailView.ComplaintRef(
                complaint.originalNo(), complaint.originalTitle(), ComplaintStatus.valueOf(complaint.originalStatus()));
        return new ComplaintDetailView(complaint.number(), complaint.title(), complaint.description(), complaint.address(),
                complaint.status(), complaint.categoryName(), complaint.areaName(), complaint.departmentName(),
                staff || owner ? complaint.ownerName() : null, complaint.createdAt(), complaint.updatedAt(), complaint.resolvedAt(),
                deadline, complaint.reopenCount(), config.maxReopens(), complaint.version(), overdue, due,
                overdue ? ComplaintDates.daysOverdue(due, now) : 0, original, count, access.relation(),
                attachments.visible(complaint.id(), AttachmentPurpose.COMPLAINT, admin),
                attachments.visible(complaint.id(), AttachmentPurpose.RESOLUTION, admin),
                timeline.timeline(complaint.id(), owner, user),
                TransitionRules.allowedActions(actor, facts, now, config.reviewWindowDays(), config.maxReopens()),
                notices(actor, facts, now, deadline));
    }

    private List<String> notices(ActorContext actor, ComplaintFacts facts, LocalDateTime now, LocalDateTime deadline) {
        String day = deadline == null ? "" : format.date(deadline.toLocalDate());
        return Arrays.stream(Action.values())
                .map(action -> TransitionRules.decide(action, actor, facts, now, config.reviewWindowDays(), config.maxReopens()))
                .filter(TransitionRules.Decision::showNotice).map(TransitionRules.Decision::refusal).distinct()
                .map(key -> messages.getMessage(key, new Object[]{day}, LocaleContextHolder.getLocale())).toList();
    }

    private static NotFoundException missing(String no) {
        return new NotFoundException("complaint", no, true);
    }
}
