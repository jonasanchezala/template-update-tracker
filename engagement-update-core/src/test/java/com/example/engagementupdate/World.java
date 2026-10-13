package com.example.engagementupdate;

import com.example.engagementupdate.domain.EngagementId;
import com.example.engagementupdate.domain.EngagementRecord;
import com.example.engagementupdate.domain.EngagementStatus;
import com.example.engagementupdate.domain.FirmId;
import com.example.engagementupdate.domain.TemplateId;
import com.example.engagementupdate.domain.Version;
import com.example.engagementupdate.events.BackfillSnapshot;
import com.example.engagementupdate.events.EngagementEvent;
import com.example.engagementupdate.events.EngagementArchived;
import com.example.engagementupdate.events.EngagementCreated;
import com.example.engagementupdate.events.UpdateApplied;
import com.example.engagementupdate.events.UpdateDeclined;
import com.example.engagementupdate.events.TemplateVersionPublished;
import com.example.engagementupdate.handlers.DefaultBackfillSeeder;
import com.example.engagementupdate.handlers.DefaultEngagementEventHandler;
import com.example.engagementupdate.handlers.DefaultFirmEngagementQuery;
import com.example.engagementupdate.handlers.DefaultImpactCounter;
import com.example.engagementupdate.handlers.DefaultTemplateVersionHandler;
import com.example.engagementupdate.handlers.HandleOutcome;
import com.example.engagementupdate.store.EngagementStore;
import com.example.engagementupdate.store.InMemoryEngagementStore;
import com.example.engagementupdate.store.InMemoryLatestVersionStore;
import com.example.engagementupdate.store.LatestVersionStore;
import java.time.Instant;

/** Wires the in-memory stores to the real handlers, and builds events with short names. */
final class World {

    static final FirmId FIRM_A = new FirmId("firm-a");
    static final FirmId FIRM_B = new FirmId("firm-b");
    static final EngagementId E = new EngagementId("E");
    static final TemplateId T = new TemplateId("T");
    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    final EngagementStore store;
    final LatestVersionStore latest;
    final DefaultEngagementEventHandler events;
    final DefaultTemplateVersionHandler versions;
    final DefaultBackfillSeeder seeder;
    final DefaultImpactCounter impact;
    final DefaultFirmEngagementQuery query;

    World() {
        this(new InMemoryEngagementStore());
    }

    World(EngagementStore store) {
        this.store = store;
        this.latest = new InMemoryLatestVersionStore();
        this.events = new DefaultEngagementEventHandler(store);
        this.versions = new DefaultTemplateVersionHandler(latest);
        this.seeder = new DefaultBackfillSeeder(store);
        this.impact = new DefaultImpactCounter(store);
        this.query = new DefaultFirmEngagementQuery(store, latest);
    }

    static Version v(int value) {
        return Version.of(value);
    }

    static EngagementCreated created(FirmId firm, EngagementId e, long seq, TemplateId t, int version) {
        return new EngagementCreated(firm, e, seq, "actor", NOW, t, v(version));
    }

    static UpdateApplied applied(FirmId firm, EngagementId e, long seq, TemplateId t, int from, int to) {
        return new UpdateApplied(firm, e, seq, "actor", NOW, t, v(from), v(to));
    }

    static UpdateDeclined declined(FirmId firm, EngagementId e, long seq, TemplateId t, int from, int to) {
        return new UpdateDeclined(firm, e, seq, "actor", NOW, t, v(from), v(to));
    }

    static EngagementArchived archived(FirmId firm, EngagementId e, long seq) {
        return new EngagementArchived(firm, e, seq, "actor", NOW);
    }

    static BackfillSnapshot snapshot(FirmId firm, EngagementId e, TemplateId t, int version) {
        return new BackfillSnapshot(firm, e, t, v(version), EngagementStatus.ACTIVE);
    }

    HandleOutcome handle(EngagementEvent event) {
        return events.handle(event);
    }

    void publish(TemplateId t, int version) {
        versions.handle(new TemplateVersionPublished(t, v(version), NOW));
    }

    EngagementRecord record(FirmId firm, EngagementId e) {
        return store.find(firm, e).orElseThrow();
    }
}
