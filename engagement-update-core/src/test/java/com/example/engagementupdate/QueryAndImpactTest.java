package com.example.engagementupdate;

import static com.example.engagementupdate.World.E;
import static com.example.engagementupdate.World.FIRM_A;
import static com.example.engagementupdate.World.FIRM_B;
import static com.example.engagementupdate.World.T;
import static com.example.engagementupdate.World.applied;
import static com.example.engagementupdate.World.archived;
import static com.example.engagementupdate.World.created;
import static com.example.engagementupdate.World.declined;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.engagementupdate.domain.DecisionRecord;
import com.example.engagementupdate.domain.EngagementId;
import com.example.engagementupdate.domain.EngagementView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryAndImpactTest {

    private static final EngagementId E2 = new EngagementId("E2");

    private final World world = new World();

    @Test
    @DisplayName("AC-10: an archived engagement is not pending and is not counted")
    void ac10_archived() {
        world.handle(created(FIRM_A, E, 1, T, 2));
        world.publish(T, 4);
        assertThat(world.query.engagementsFor(FIRM_A)).singleElement().extracting(EngagementView::pending).isEqualTo(true);
        assertThat(world.impact.count(T, World.v(4))).containsEntry(FIRM_A, 1L);

        world.handle(archived(FIRM_A, E, 2));

        assertThat(world.query.engagementsFor(FIRM_A)).singleElement().extracting(EngagementView::pending).isEqualTo(false);
        assertThat(world.impact.count(T, World.v(4))).containsEntry(FIRM_A, 0L);
    }

    @Test
    @DisplayName("AC-11: a firm with engagements on the template but none affected appears with 0")
    void ac11_zeroFirmsAppear() {
        world.handle(created(FIRM_A, E, 1, T, 5));
        world.handle(created(FIRM_B, E, 1, T, 2));

        assertThat(world.impact.count(T, World.v(5)))
                .containsEntry(FIRM_A, 0L)
                .containsEntry(FIRM_B, 1L)
                .hasSize(2);
    }

    @Test
    @DisplayName("AC-12: a firm's queries return only that firm's data")
    void ac12_tenantScoping() {
        world.handle(created(FIRM_A, E, 1, T, 1));
        world.handle(declined(FIRM_A, E, 2, T, 1, 2));
        world.handle(created(FIRM_B, E2, 1, T, 1));
        world.handle(applied(FIRM_B, E2, 2, T, 1, 3));

        assertThat(world.query.engagementsFor(FIRM_A)).extracting(EngagementView::engagementId).containsExactly(E);
        assertThat(world.query.engagementsFor(FIRM_B)).extracting(EngagementView::engagementId).containsExactly(E2);
        assertThat(world.query.decisionHistoryFor(FIRM_A)).extracting(DecisionRecord::firmId).containsOnly(FIRM_A).hasSize(1);
        assertThat(world.query.decisionHistoryFor(FIRM_B)).extracting(DecisionRecord::firmId).containsOnly(FIRM_B).hasSize(1);
    }

    @Test
    @DisplayName("AC-12: the same engagement id in two firms is two separate engagements")
    void ac12_sameIdTwoFirms() {
        world.handle(created(FIRM_A, E, 1, T, 1));
        world.handle(created(FIRM_B, E, 1, T, 4));

        assertThat(world.record(FIRM_A, E).appliedVersion()).isEqualTo(World.v(1));
        assertThat(world.record(FIRM_B, E).appliedVersion()).isEqualTo(World.v(4));
    }

    @Test
    @DisplayName("R8: counts only engagements behind the version, and only on that template")
    void r8_countsBehindOnly() {
        world.handle(created(FIRM_A, E, 1, T, 2));
        world.handle(created(FIRM_A, E2, 1, T, 5));
        world.handle(created(FIRM_A, new EngagementId("E3"), 1, new com.example.engagementupdate.domain.TemplateId("other"), 1));

        assertThat(world.impact.count(T, World.v(5))).containsExactly(java.util.Map.entry(FIRM_A, 1L));
    }

    @Test
    @DisplayName("R8: a declined engagement is not counted for the version it declined through")
    void r8_declinedNotCounted() {
        world.handle(created(FIRM_A, E, 1, T, 2));
        world.handle(declined(FIRM_A, E, 2, T, 2, 4));

        assertThat(world.impact.count(T, World.v(4))).containsEntry(FIRM_A, 0L);
        assertThat(world.impact.count(T, World.v(5))).containsEntry(FIRM_A, 1L);
    }

    @Test
    @DisplayName("R8: a template nobody uses has an empty result")
    void r8_noEngagements() {
        assertThat(world.impact.count(T, World.v(3))).isEmpty();
    }

    @Test
    @DisplayName("R7: the list shows an unknown latest version as not pending")
    void r7_unknownLatest() {
        world.handle(created(FIRM_A, E, 1, T, 2));

        assertThat(world.query.engagementsFor(FIRM_A))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.latestVersion()).isEmpty();
                    assertThat(view.pending()).isFalse();
                });
    }
}
