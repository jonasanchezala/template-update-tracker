package com.example.engagementupdate;

import static com.example.engagementupdate.World.E;
import static com.example.engagementupdate.World.FIRM_A;
import static com.example.engagementupdate.World.NOW;
import static com.example.engagementupdate.World.T;
import static com.example.engagementupdate.World.applied;
import static com.example.engagementupdate.World.created;
import static com.example.engagementupdate.World.snapshot;
import static com.example.engagementupdate.World.v;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.engagementupdate.domain.EngagementRecord;
import com.example.engagementupdate.domain.EngagementStatus;
import com.example.engagementupdate.domain.RecordSource;
import com.example.engagementupdate.events.BackfillSnapshot;
import com.example.engagementupdate.events.TemplateVersionPublished;
import com.example.engagementupdate.handlers.SeedOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemplateVersionAndBackfillTest {

    private final World world = new World();

    @Test
    @DisplayName("AC-7: backfill never overwrites a record the hook already made")
    void ac7_seedSkipsExisting() {
        world.handle(created(FIRM_A, E, 1, T, 3));
        EngagementRecord before = world.record(FIRM_A, E);

        SeedOutcome outcome = world.seeder.seed(snapshot(FIRM_A, E, T, 2));

        assertThat(outcome).isEqualTo(SeedOutcome.SKIPPED);
        assertThat(world.record(FIRM_A, E)).isEqualTo(before);
        assertThat(world.record(FIRM_A, E).appliedVersion()).isEqualTo(v(3));
    }

    @Test
    @DisplayName("AC-8: a hook event applies on top of a backfilled record")
    void ac8_hookAppliesOnTopOfSeed() {
        SeedOutcome outcome = world.seeder.seed(snapshot(FIRM_A, E, T, 2));
        world.handle(applied(FIRM_A, E, 1, T, 2, 3));

        assertThat(outcome).isEqualTo(SeedOutcome.CREATED);
        assertThat(world.record(FIRM_A, E).appliedVersion()).isEqualTo(v(3));
    }

    @Test
    @DisplayName("AC-9: a lower published version does not lower the latest")
    void ac9_latestNeverLowers() {
        world.publish(T, 5);

        boolean changed = world.versions.handle(new TemplateVersionPublished(T, v(4), NOW));

        assertThat(changed).isFalse();
        assertThat(world.latest.latest(T)).contains(v(5));
    }

    @Test
    @DisplayName("R5: a higher version raises the latest, an equal one changes nothing")
    void r5_raise() {
        assertThat(world.versions.handle(new TemplateVersionPublished(T, v(3), NOW))).isTrue();
        assertThat(world.versions.handle(new TemplateVersionPublished(T, v(5), NOW))).isTrue();
        assertThat(world.versions.handle(new TemplateVersionPublished(T, v(5), NOW))).isFalse();
        assertThat(world.latest.latest(T)).contains(v(5));
    }

    @Test
    @DisplayName("R6: a seeded record has lastSeq 0, source BACKFILL, and no decision history")
    void r6_seededRecordShape() {
        world.seeder.seed(new BackfillSnapshot(FIRM_A, E, T, v(2), EngagementStatus.ARCHIVED));

        EngagementRecord record = world.record(FIRM_A, E);
        assertThat(record.lastSeq()).isZero();
        assertThat(record.source()).isEqualTo(RecordSource.BACKFILL);
        assertThat(record.status()).isEqualTo(EngagementStatus.ARCHIVED);
        assertThat(record.declinedThroughVersion()).isEmpty();
        assertThat(world.query.decisionHistoryFor(FIRM_A)).isEmpty();
    }

    @Test
    @DisplayName("R6: seeding twice creates once")
    void r6_seedTwice() {
        assertThat(world.seeder.seed(snapshot(FIRM_A, E, T, 2))).isEqualTo(SeedOutcome.CREATED);
        assertThat(world.seeder.seed(snapshot(FIRM_A, E, T, 7))).isEqualTo(SeedOutcome.SKIPPED);
        assertThat(world.record(FIRM_A, E).appliedVersion()).isEqualTo(v(2));
    }
}
