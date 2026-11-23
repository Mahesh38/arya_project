package com.smartgrievance.complaint;

import com.smartgrievance.access.ComplaintAccessPolicy;
import com.smartgrievance.access.ComplaintAccessPolicy.Relation;
import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.user.Role;
import com.smartgrievance.workflow.ActorContext;
import org.springframework.stereotype.Service;

@Service
public class ComplaintVisibility {
    private final ComplaintRepository complaints;

    ComplaintVisibility(ComplaintRepository complaints) {
        this.complaints = complaints;
    }

    public Relation relation(CurrentUser actor, long complaintId, long ownerId, Long departmentId) {
        return access(ActorContext.of(actor), complaintId, ownerId, departmentId).relation();
    }

    public record Access(Relation relation, boolean watching, boolean linked) {}

    public Access access(ActorContext actor, long complaintId, long ownerId, Long departmentId) {
        boolean otherCitizen = actor.role() == Role.CITIZEN && actor.userId() != ownerId;
        var facts = otherCitizen ? complaints.viewerFacts(complaintId, actor.userId())
                : new ComplaintRepository.ViewerFacts(false, false);
        return new Access(ComplaintAccessPolicy.relation(actor, ownerId, departmentId, facts.watching(), facts.linked()),
                facts.watching(), facts.linked());
    }
}
