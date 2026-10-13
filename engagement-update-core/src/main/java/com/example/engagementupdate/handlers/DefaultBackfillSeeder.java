package com.example.engagementupdate.handlers;

import com.example.engagementupdate.domain.EngagementRecord;
import com.example.engagementupdate.domain.RecordSource;
import com.example.engagementupdate.events.BackfillSnapshot;
import com.example.engagementupdate.store.EngagementStore;
import java.util.Objects;
import java.util.Optional;

/**
 * R6. A single conditional write: it can create a record, never change one. The record starts at
 * {@code lastSeq = 0}, so any hook event (seq of 1 or more) applies on top of it.
 */
public final class DefaultBackfillSeeder implements BackfillSeeder {

    private final EngagementStore store;

    public DefaultBackfillSeeder(EngagementStore store) {
        this.store = Objects.requireNonNull(store);
    }

    @Override
    public SeedOutcome seed(BackfillSnapshot snapshot) {
        EngagementRecord record = new EngagementRecord(
                snapshot.firmId(),
                snapshot.engagementId(),
                snapshot.templateId(),
                snapshot.version(),
                Optional.empty(),
                snapshot.status(),
                0,
                RecordSource.BACKFILL);
        return store.putIfAbsent(record) ? SeedOutcome.CREATED : SeedOutcome.SKIPPED;
    }
}
