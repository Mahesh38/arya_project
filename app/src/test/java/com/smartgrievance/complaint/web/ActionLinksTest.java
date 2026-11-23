package com.smartgrievance.complaint.web;

import com.smartgrievance.complaint.web.ActionLinks.ActionLink;
import com.smartgrievance.user.Role;
import com.smartgrievance.workflow.TransitionRules.Action;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class ActionLinksTest {
    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("TC-UT-12 action links preserve order, method, style, and role route prefix")
    void actionLinks(Role role) {
        String number = "GRV-2026-000001";
        var links = ActionLinks.forComplaint(number, role, EnumSet.allOf(Action.class));
        assertThat(links).extracting(ActionLink::action).containsExactly(Action.values());
        String staff = role == Role.ADMIN ? "admin" : "officer";
        var routes = List.of("citizen/edit", "citizen/withdraw", "citizen/close", "citizen/reopen", "citizen/unwatch",
                "citizen/watch", "admin/review", "admin/assign", "admin/reassign", staff + "/start", staff + "/remark",
                staff + "/resolve", "officer/send-back", staff + "/reject", staff + "/duplicate");
        var styles = List.of("secondary", "danger", "success", "primary", "secondary", "secondary", "primary",
                "primary", "primary", "primary", "primary", "success", "secondary", "danger", "secondary");
        for (int i = 0; i < links.size(); i++) {
            var link = links.get(i);
            var route = routes.get(i).split("/");
            assertThat(link.url()).isEqualTo("/" + route[0] + "/complaints/" + number + "/" + route[1]);
            assertThat(link.style()).isEqualTo(styles.get(i));
            boolean directPost = EnumSet.of(Action.START_REVIEW, Action.UNWATCH, Action.WATCH).contains(link.action());
            assertThat(link.method()).isEqualTo(directPost ? HttpMethod.POST : HttpMethod.GET);
            assertThat(link.confirm()).isEqualTo(directPost);
        }
    }
}
