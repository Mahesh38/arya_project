package com.smartgrievance.workflow;

import com.smartgrievance.access.ComplaintAccessPolicy;
import com.smartgrievance.common.exception.InvalidTransitionException;
import com.smartgrievance.common.exception.Subject;
import com.smartgrievance.complaint.ComplaintStatus;
import com.smartgrievance.user.Role;
import com.smartgrievance.workflow.TransitionRules.Action;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

import static com.smartgrievance.complaint.ComplaintStatus.*;
import static com.smartgrievance.workflow.TransitionRules.Action.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransitionRulesTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 10, 0);
    private static final ActorContext OWNER = new ActorContext(1, Role.CITIZEN, null);
    private static final ActorContext OTHER = new ActorContext(2, Role.CITIZEN, null);
    private static final ActorContext OFFICER = new ActorContext(3, Role.OFFICER, 4L);
    private static final ActorContext ADMIN = new ActorContext(4, Role.ADMIN, null);
    private static final Subject SUBJECT = new Subject(Subject.Type.COMPLAINT, "GRV-2026-000001");

    record Viewer(String code, ActorContext actor, boolean watching, boolean ownsDuplicate) {}
    record Cell(Action action, ComplaintStatus status, Viewer viewer, boolean expected) {}
    record Row(ComplaintStatus status, Viewer viewer, EnumSet<Action> expected) {}

    private static final List<Viewer> VIEWERS = List.of(new Viewer("O", OWNER, false, false),
            new Viewer("W", OTHER, true, false), new Viewer("D", OTHER, false, true),
            new Viewer("F", OFFICER, false, false), new Viewer("A", ADMIN, false, false),
            new Viewer("X", OTHER, false, false));

    private static EnumMap<Action, String[]> matrix() throws Exception {
        String csv = new ClassPathResource("transition-matrix.csv").getContentAsString(StandardCharsets.UTF_8);
        var rows = new EnumMap<Action, String[]>(Action.class);
        csv.lines().skip(1).map(line -> line.split(",", -1))
                .forEach(fields -> rows.put(Action.valueOf(fields[0]), fields));
        return rows;
    }

    private static int column(ComplaintStatus status) {
        return switch (status) {
            case RESOLVED -> ComplaintStatus.REOPENED.ordinal() + 1;
            case REOPENED -> ComplaintStatus.RESOLVED.ordinal() + 1;
            default -> status.ordinal() + 1;
        };
    }

    static Stream<Cell> cells() throws Exception {
        var matrix = matrix();
        return Stream.of(ComplaintStatus.values()).flatMap(status -> VIEWERS.stream().flatMap(viewer ->
                Stream.of(Action.values()).map(action -> new Cell(action, status, viewer,
                        matrix.get(action)[column(status)].contains(viewer.code())))));
    }

    static Stream<Row> rows() throws Exception {
        var matrix = matrix();
        return Stream.of(ComplaintStatus.values()).flatMap(status -> VIEWERS.stream().map(viewer -> {
            var expected = EnumSet.noneOf(Action.class);
            for (Action action : Action.values()) {
                if (matrix.get(action)[column(status)].contains(viewer.code())) expected.add(action);
            }
            return new Row(status, viewer, expected);
        }));
    }

    @ParameterizedTest(name = "TC-SM-30 {0}")
    @MethodSource("cells")
    void fullMatrix(Cell cell) {
        var viewer = cell.viewer();
        var facts = new ComplaintFacts(cell.status(), 1, 4L, 0, NOW.minusDays(1), false,
                viewer.watching(), viewer.ownsDuplicate());
        assertThat(TransitionRules.decide(cell.action(), viewer.actor(), facts, NOW, 7, 2).allowed())
                .isEqualTo(cell.expected());
    }

    @ParameterizedTest(name = "TC-SM-31 {0}")
    @MethodSource("rows")
    void allowedActionsMatchesMatrix(Row row) {
        var viewer = row.viewer();
        var facts = new ComplaintFacts(row.status(), 1, 4L, 0, NOW.minusDays(1), false,
                viewer.watching(), viewer.ownsDuplicate());
        assertThat(TransitionRules.allowedActions(viewer.actor(), facts, NOW, 7, 2)).isEqualTo(row.expected());
    }

    @Test
    @DisplayName("TC-SM-33 final complaints are read-only except for watcher toggles")
    void finalStatusesOnlyAllowWatchToggles() {
        for (var status : List.of(CLOSED, REJECTED, DUPLICATE, WITHDRAWN)) {
            for (var viewer : VIEWERS) {
                var facts = new ComplaintFacts(status, OWNER.userId(), OFFICER.departmentId(), 0,
                        NOW.minusDays(1), false, viewer.watching(), viewer.ownsDuplicate());
                var expected = switch (viewer.code()) {
                    case "W" -> EnumSet.of(UNWATCH);
                    case "D" -> EnumSet.of(WATCH);
                    default -> EnumSet.noneOf(Action.class);
                };
                assertThat(TransitionRules.allowedActions(viewer.actor(), facts, NOW, 7, 2)).isEqualTo(expected);
            }
        }
    }

    @Test
    @DisplayName("TC-SM-38 the owner gets the precise withdrawal refusal for the current status")
    void withdrawalMessages() {
        for (var status : ComplaintStatus.values()) {
            var decision = TransitionRules.decide(WITHDRAW, OWNER,
                    new ComplaintFacts(status, 1, 4L, 0, NOW, false, false, false), NOW, 7, 2);
            if (status == SUBMITTED || status == UNDER_REVIEW || status == ASSIGNED) {
                assertThat(decision.allowed()).isTrue();
            } else {
                assertThat(decision.refusal()).isEqualTo(switch (status) {
                    case IN_PROGRESS, RESOLVED, REOPENED -> "error.withdraw.workStarted";
                    default -> "error.state.notAllowed";
                });
            }
        }
    }

    @Test
    void additionalGuardsAndRequiredActions() {
        var linked = new ComplaintFacts(ASSIGNED, 1, 4L, 0, null, true, false, false);
        assertThat(TransitionRules.decide(WITHDRAW, OWNER, linked, NOW, 7, 2).showNotice()).isTrue();
        assertThatThrownBy(() -> TransitionRules.require(WITHDRAW, OWNER, linked, NOW, 7, 2, SUBJECT))
                .isInstanceOf(InvalidTransitionException.class);
        TransitionRules.require(START_WORK, OFFICER, linked, NOW, 7, 2, SUBJECT);

        var watching = new ComplaintFacts(ASSIGNED, 1, 4L, 0, null, false, true, true);
        assertThat(TransitionRules.decide(WATCH, OTHER, watching, NOW, 7, 2).refusal())
                .isEqualTo("error.watch.alreadyWatching");
        assertThat(TransitionRules.decide(UNWATCH, OTHER, watching, NOW, 7, 2).allowed()).isTrue();

        var expired = new ComplaintFacts(RESOLVED, 1, 4L, 0, NOW.minusDays(8), false, false, false);
        var exhausted = new ComplaintFacts(RESOLVED, 1, 4L, 2, NOW.minusDays(7), false, false, false);
        var absent = new ComplaintFacts(RESOLVED, 1, 4L, 0, null, false, false, false);
        assertThat(TransitionRules.decide(CLOSE, OWNER, expired, NOW, 7, 2).refusal()).isEqualTo("error.review.ended");
        assertThat(TransitionRules.decide(CLOSE, OWNER, absent, NOW, 7, 2).refusal()).isEqualTo("error.review.ended");
        assertThat(TransitionRules.decide(CLOSE, OWNER, exhausted, NOW, 7, 2).allowed()).isTrue();
        assertThat(TransitionRules.decide(REOPEN, OWNER, exhausted, NOW, 7, 2).refusal()).isEqualTo("error.reopen.limit");
        assertThat(TransitionRules.canAutoClose(RESOLVED, NOW, expired.resolvedAt(), 7)).isTrue();
        assertThat(TransitionRules.canAutoClose(RESOLVED, NOW, exhausted.resolvedAt(), 7)).isFalse();
        assertThat(TransitionRules.canAutoClose(RESOLVED, NOW, null, 7)).isFalse();
        assertThat(TransitionRules.canAutoClose(SUBMITTED, NOW, expired.resolvedAt(), 7)).isFalse();
    }

    @Test
    @DisplayName("TC-SM-41 watchers and linked owners receive the precise owner-only refusal")
    void viewersNeverTakeOwnerActions() {
        var ownerActions = EnumSet.of(EDIT, WITHDRAW, CLOSE, REOPEN);
        for (var viewer : VIEWERS.stream().filter(candidate -> candidate.watching() || candidate.ownsDuplicate()).toList()) {
            for (ComplaintStatus status : ComplaintStatus.values()) {
                var facts = new ComplaintFacts(status, OWNER.userId(), OFFICER.departmentId(), 0,
                        NOW.minusDays(1), false, viewer.watching(), viewer.ownsDuplicate());
                for (Action action : ownerActions) {
                    if (TransitionRules.allows(action, status, ComplaintAccessPolicy.Relation.OWNER)) {
                        var decision = TransitionRules.decide(action, viewer.actor(), facts, NOW, 7, 2);
                        assertThat(decision.allowed()).isFalse();
                        assertThat(decision.refusal()).isEqualTo("error.owner.only");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("TC-UT-01 view access distinguishes owner, watched and linked cases and the current department")
    void viewAccess() {
        for (var viewer : VIEWERS) {
            assertThat(ComplaintAccessPolicy.canView(ComplaintAccessPolicy.relation(viewer.actor(),
                    1, 4L, viewer.watching(), viewer.ownsDuplicate()))).isEqualTo(!viewer.code().equals("X"));
        }
        assertThat(ComplaintAccessPolicy.canView(ComplaintAccessPolicy.relation(OFFICER, 1, null, false, false))).isFalse();
        assertThat(ComplaintAccessPolicy.canView(ComplaintAccessPolicy.relation(OFFICER, 1, 9L, false, false))).isFalse();
    }
}
