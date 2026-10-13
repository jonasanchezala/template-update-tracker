package com.example.engagementupdate;

import static com.example.engagementupdate.World.E;
import static com.example.engagementupdate.World.FIRM_A;
import static com.example.engagementupdate.World.T;
import static com.example.engagementupdate.World.applied;
import static com.example.engagementupdate.World.archived;
import static com.example.engagementupdate.World.created;
import static com.example.engagementupdate.World.declined;
import static com.example.engagementupdate.World.v;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.engagementupdate.domain.DecisionOutcome;
import com.example.engagementupdate.domain.EngagementId;
import com.example.engagementupdate.domain.EngagementRecord;
import com.example.engagementupdate.domain.EngagementStatus;
import com.example.engagementupdate.domain.EngagementView;
import com.example.engagementupdate.domain.FirmId;
import com.example.engagementupdate.domain.RecordSource;
import com.example.engagementupdate.domain.TemplateId;
import com.example.engagementupdate.handlers.HandleOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EngagementEventHandlerTest {

    private final World world = new World();

    private EngagementView onlyView() {
        assertThat(world.query.engagementsFor(FIRM_A)).hasSize(1);
        return world.query.engagementsFor(FIRM_A).get(0);
    }

    @Test
    @DisplayName("AC-1: created records the engagement at its version, active, not pending")
    void ac1_created() {
        HandleOutcome outcome = world.handle(created(FIRM_A, E, 1, T, 5));
        world.publish(T, 5);

        assertThat(outcome).isEqualTo(HandleOutcome.APPLIED);
        EngagementRecord record = world.record(FIRM_A, E);
        assertThat(record.appliedVersion()).isEqualTo(v(5));
        assertThat(record.declinedThroughVersion()).isEmpty();
        assertThat(record.status()).isEqualTo(EngagementStatus.ACTIVE);
        assertThat(record.lastSeq()).isEqualTo(1);
        assertThat(record.source()).isEqualTo(RecordSource.HOOK);
        assertThat(onlyView().pending()).isFalse();
    }

    @Test
    @DisplayName("AC-2: applied v2 with latest v4 is pending")
    void ac2_pending() {
        world.handle(created(FIRM_A, E, 1, T, 2));
        world.publish(T, 4);

        assertThat(onlyView().pending()).isTrue();
    }

    @Test
    @DisplayName("AC-3: decline moves the baseline, not the applied version, and records one decision")
    void ac3_decline() {
        world.handle(created(FIRM_A, E, 1, T, 2));
        world.publish(T, 4);

        HandleOutcome outcome = world.handle(declined(FIRM_A, E, 2, T, 2, 4));

        assertThat(outcome).isEqualTo(HandleOutcome.APPLIED);
        EngagementRecord record = world.record(FIRM_A, E);
        assertThat(record.declinedThroughVersion()).contains(v(4));
        assertThat(record.appliedVersion()).isEqualTo(v(2));
        assertThat(onlyView().pending()).isFalse();
        assertThat(world.query.decisionHistoryFor(FIRM_A))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.outcome()).isEqualTo(DecisionOutcome.DECLINED);
                    assertThat(d.fromVersion()).isEqualTo(v(2));
                    assertThat(d.toVersion()).isEqualTo(v(4));
                });
    }

    @Test
    @DisplayName("AC-4: after a decline, a newer published version makes it pending again from the applied version")
    void ac4_pendingAgainWithRange() {
        world.handle(created(FIRM_A, E, 1, T, 2));
        world.publish(T, 4);
        world.handle(declined(FIRM_A, E, 2, T, 2, 4));

        world.publish(T, 5);

        EngagementView view = onlyView();
        assertThat(view.pending()).isTrue();
        // The summary range runs from the applied version to the latest: v2 -> v5.
        assertThat(view.appliedVersion()).isEqualTo(v(2));
        assertThat(view.latestVersion()).contains(v(5));
    }

    @Test
    @DisplayName("AC-5: an apply delivered twice applies once and records one decision")
    void ac5_duplicateApply() {
        world.handle(created(FIRM_A, E, 2, T, 2)); // seq 2 so the apply below is seq 3

        HandleOutcome first = world.handle(applied(FIRM_A, E, 3, T, 2, 5));
        HandleOutcome second = world.handle(applied(FIRM_A, E, 3, T, 2, 5));

        assertThat(first).isEqualTo(HandleOutcome.APPLIED);
        assertThat(second).isEqualTo(HandleOutcome.IGNORED_STALE);
        assertThat(world.record(FIRM_A, E).appliedVersion()).isEqualTo(v(5));
        assertThat(world.query.decisionHistoryFor(FIRM_A))
                .singleElement()
                .satisfies(d -> assertThat(d.outcome()).isEqualTo(DecisionOutcome.APPLIED));
    }

    @Test
    @DisplayName("AC-6: an older event after a newer one changes nothing")
    void ac6_staleEvent() {
        world.handle(created(FIRM_A, E, 4, T, 2));
        EngagementRecord before = world.record(FIRM_A, E);

        HandleOutcome outcome = world.handle(applied(FIRM_A, E, 3, T, 2, 9));

        assertThat(outcome).isEqualTo(HandleOutcome.IGNORED_STALE);
        assertThat(world.record(FIRM_A, E)).isEqualTo(before);
        assertThat(world.query.decisionHistoryFor(FIRM_A)).isEmpty();
    }

    @Test
    @DisplayName("AC-13: archive for an unknown engagement is ignored and creates nothing")
    void ac13_archiveUnknown() {
        HandleOutcome outcome = world.handle(archived(FIRM_A, E, 1));

        assertThat(outcome).isEqualTo(HandleOutcome.IGNORED_UNKNOWN);
        assertThat(world.store.find(FIRM_A, E)).isEmpty();
    }

    // ---- rules the acceptance criteria do not spell out ----

    @Test
    @DisplayName("R1a: an apply for an unknown engagement creates the record from the event's own fields")
    void r1a_applyCreatesRecord() {
        HandleOutcome outcome = world.handle(applied(FIRM_A, E, 5, T, 2, 4));

        assertThat(outcome).isEqualTo(HandleOutcome.APPLIED);
        EngagementRecord record = world.record(FIRM_A, E);
        assertThat(record.appliedVersion()).isEqualTo(v(4));
        assertThat(record.declinedThroughVersion()).isEmpty();
        assertThat(record.lastSeq()).isEqualTo(5);
        assertThat(world.query.decisionHistoryFor(FIRM_A)).hasSize(1);
    }

    @Test
    @DisplayName("R1a: a decline for an unknown engagement creates the record at the version it declined from")
    void r1a_declineCreatesRecord() {
        world.handle(declined(FIRM_A, E, 5, T, 2, 4));

        EngagementRecord record = world.record(FIRM_A, E);
        assertThat(record.appliedVersion()).isEqualTo(v(2));
        assertThat(record.declinedThroughVersion()).contains(v(4));
        assertThat(world.query.decisionHistoryFor(FIRM_A)).hasSize(1);
    }

    @Test
    @DisplayName("R4: a lower decline never lowers declined-through")
    void r4_declineKeepsMax() {
        world.handle(created(FIRM_A, E, 1, T, 1));
        world.handle(declined(FIRM_A, E, 2, T, 1, 5));

        world.handle(declined(FIRM_A, E, 3, T, 1, 3));

        assertThat(world.record(FIRM_A, E).declinedThroughVersion()).contains(v(5));
    }

    @Test
    @DisplayName("R3: apply leaves declined-through unchanged")
    void r3_applyKeepsDeclined() {
        world.handle(created(FIRM_A, E, 1, T, 1));
        world.handle(declined(FIRM_A, E, 2, T, 1, 3));

        world.handle(applied(FIRM_A, E, 3, T, 1, 2));

        EngagementRecord record = world.record(FIRM_A, E);
        assertThat(record.appliedVersion()).isEqualTo(v(2));
        assertThat(record.declinedThroughVersion()).contains(v(3));
    }

    @Test
    @DisplayName("R4a: archive changes only the status (and the sequence number)")
    void r4a_archiveOnlyStatus() {
        world.handle(created(FIRM_A, E, 1, T, 2));
        world.handle(declined(FIRM_A, E, 2, T, 2, 3));
        EngagementRecord before = world.record(FIRM_A, E);

        HandleOutcome outcome = world.handle(archived(FIRM_A, E, 3));

        assertThat(outcome).isEqualTo(HandleOutcome.APPLIED);
        EngagementRecord after = world.record(FIRM_A, E);
        assertThat(after.status()).isEqualTo(EngagementStatus.ARCHIVED);
        assertThat(after).isEqualTo(new EngagementRecord(
                before.firmId(),
                before.engagementId(),
                before.templateId(),
                before.appliedVersion(),
                before.declinedThroughVersion(),
                EngagementStatus.ARCHIVED,
                3,
                before.source()));
        assertThat(world.query.decisionHistoryFor(FIRM_A)).hasSize(1); // only the decline
    }

    @Test
    @DisplayName("R2: a duplicate create does not reset state")
    void r2_duplicateCreate() {
        world.handle(created(FIRM_A, E, 1, T, 2));
        world.handle(applied(FIRM_A, E, 2, T, 2, 4));

        HandleOutcome outcome = world.handle(created(FIRM_A, E, 1, T, 2));

        assertThat(outcome).isEqualTo(HandleOutcome.IGNORED_STALE);
        assertThat(world.record(FIRM_A, E).appliedVersion()).isEqualTo(v(4));
    }

    @Test
    @DisplayName("R9: history is oldest first")
    void r9_historyOrder() {
        world.handle(created(FIRM_A, E, 1, T, 1));
        world.handle(declined(FIRM_A, E, 2, T, 1, 2));
        world.handle(applied(FIRM_A, E, 3, T, 1, 3));

        assertThat(world.query.decisionHistoryFor(FIRM_A))
                .extracting(d -> d.outcome())
                .containsExactly(DecisionOutcome.DECLINED, DecisionOutcome.APPLIED);
    }

    /**
     * Known consequence of R1, pinned so nobody is surprised by it: R1 drops an event older than the
     * record's lastSeq rather than merging it, so a decline that arrives after a newer apply is lost.
     * This relies on the queue delivering one engagement's events in order (design: FIFO per engagement).
     */
    @Test
    @DisplayName("R1: an older event arriving late is dropped, not merged")
    void olderEventArrivingLateIsLost() {
        world.handle(created(FIRM_A, E, 1, T, 1));
        world.handle(applied(FIRM_A, E, 3, T, 1, 3));

        HandleOutcome late = world.handle(declined(FIRM_A, E, 2, T, 1, 4));

        assertThat(late).isEqualTo(HandleOutcome.IGNORED_STALE);
        assertThat(world.record(FIRM_A, E).declinedThroughVersion()).isEmpty();
    }

    /** Known consequence of R1a: an archive that beats its create leaves no trace, so redelivery can still change state. */
    @Test
    @DisplayName("R1a: an archive that arrives before the create is lost until it is redelivered")
    void archiveBeforeCreateIsLost() {
        world.handle(archived(FIRM_A, E, 2));
        world.handle(created(FIRM_A, E, 1, T, 1));
        assertThat(world.record(FIRM_A, E).status()).isEqualTo(EngagementStatus.ACTIVE);

        world.handle(archived(FIRM_A, E, 2)); // redelivered

        assertThat(world.record(FIRM_A, E).status()).isEqualTo(EngagementStatus.ARCHIVED);
    }

    @Test
    @DisplayName("Value types reject blank ids, non-positive versions and non-positive sequence numbers")
    void invalidValuesAreRejected() {
        assertThatThrownBy(() -> new FirmId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EngagementId("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TemplateId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> v(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> created(FIRM_A, E, 0, T, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
