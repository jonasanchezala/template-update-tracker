package com.example.engagementupdate.handlers;

import com.example.engagementupdate.domain.DecisionOutcome;
import com.example.engagementupdate.domain.DecisionRecord;
import com.example.engagementupdate.domain.EngagementRecord;
import com.example.engagementupdate.domain.EngagementStatus;
import com.example.engagementupdate.domain.RecordSource;
import com.example.engagementupdate.domain.TemplateId;
import com.example.engagementupdate.domain.Version;
import com.example.engagementupdate.events.EngagementEvent;
import com.example.engagementupdate.events.EngagementArchived;
import com.example.engagementupdate.events.EngagementCreated;
import com.example.engagementupdate.events.UpdateApplied;
import com.example.engagementupdate.events.UpdateDeclined;
import com.example.engagementupdate.store.EngagementStore;
import java.util.Objects;
import java.util.Optional;

/**
 * R1 to R4a and R9. The sequence-number check is decided by the store's conditional write, so two
 * workers racing on one engagement cannot both win. Losing a race means re-reading and deciding
 * again, which ends in either a newer record to apply on top of, or {@code IGNORED_STALE}.
 */
public final class DefaultEngagementEventHandler implements EngagementEventHandler {

    private final EngagementStore store;

    public DefaultEngagementEventHandler(EngagementStore store) {
        this.store = Objects.requireNonNull(store);
    }

    @Override
    public HandleOutcome handle(EngagementEvent event) {
        while (true) {
            Optional<EngagementRecord> existing = store.find(event.firmId(), event.engagementId());

            if (existing.isEmpty()) {
                Optional<EngagementRecord> fresh = newRecordFrom(event);
                if (fresh.isEmpty()) {
                    return HandleOutcome.IGNORED_UNKNOWN; // R1a: archive with no template to record
                }
                if (store.putIfAbsent(fresh.get())) {
                    recordDecision(event);
                    return HandleOutcome.APPLIED;
                }
                continue; // someone created it first: decide again against their record
            }

            EngagementRecord current = existing.get();
            if (event.seq() <= current.lastSeq()) {
                return HandleOutcome.IGNORED_STALE; // R1
            }
            if (store.replaceIfSeqNewer(transition(current, event))) {
                recordDecision(event);
                return HandleOutcome.APPLIED;
            }
            // lost a race with another writer: re-read
        }
    }

    /** R1a and R2: the record an event creates when none exists. */
    private static Optional<EngagementRecord> newRecordFrom(EngagementEvent event) {
        return switch (event) {
            case EngagementCreated e -> Optional.of(record(e, e.templateId(), e.version(), Optional.empty()));
            case UpdateApplied e -> Optional.of(record(e, e.templateId(), e.toVersion(), Optional.empty()));
            case UpdateDeclined e -> Optional.of(record(e, e.templateId(), e.fromVersion(), Optional.of(e.toVersion())));
            case EngagementArchived e -> Optional.empty();
            default -> throw new IllegalStateException("Unexpected value: " + event);
        };
    }

    private static EngagementRecord record(
            EngagementEvent event,
            TemplateId templateId,
            Version applied,
            Optional<Version> declinedThrough) {
        return new EngagementRecord(
                event.firmId(),
                event.engagementId(),
                templateId,
                applied,
                declinedThrough,
                EngagementStatus.ACTIVE,
                event.seq(),
                RecordSource.HOOK);
    }

    /** R2 to R4a: the record after the event, given a record whose seq the event beats. */
    private static EngagementRecord transition(EngagementRecord current, EngagementEvent event) {
        return switch (event) {
            case EngagementCreated e -> record(e, e.templateId(), e.version(), Optional.empty());
            case UpdateApplied e -> new EngagementRecord(
                    current.firmId(),
                    current.engagementId(),
                    current.templateId(),
                    e.toVersion(),
                    current.declinedThroughVersion(),
                    current.status(),
                    e.seq(),
                    current.source());
            case UpdateDeclined e -> new EngagementRecord(
                    current.firmId(),
                    current.engagementId(),
                    current.templateId(),
                    current.appliedVersion(),
                    Optional.of(current.declinedThroughVersion()
                            .map(declined -> Version.max(declined, e.toVersion()))
                            .orElse(e.toVersion())),
                    current.status(),
                    e.seq(),
                    current.source());
            case EngagementArchived e -> new EngagementRecord(
                    current.firmId(),
                    current.engagementId(),
                    current.templateId(),
                    current.appliedVersion(),
                    current.declinedThroughVersion(),
                    EngagementStatus.ARCHIVED,
                    e.seq(),
                    current.source());
            default -> throw new IllegalStateException("Unexpected value: " + event);
        };
    }

    /** R9: only apply and decline leave a decision record, and only once their write has won. */
    private void recordDecision(EngagementEvent event) {
        switch (event) {
            case UpdateApplied e -> store.appendDecision(new DecisionRecord(
                    e.firmId(),
                    e.engagementId(),
                    e.templateId(),
                    DecisionOutcome.APPLIED,
                    e.fromVersion(),
                    e.toVersion(),
                    e.actorId(),
                    e.occurredAt()));
            case UpdateDeclined e -> store.appendDecision(new DecisionRecord(
                    e.firmId(),
                    e.engagementId(),
                    e.templateId(),
                    DecisionOutcome.DECLINED,
                    e.fromVersion(),
                    e.toVersion(),
                    e.actorId(),
                    e.occurredAt()));
            case EngagementCreated e -> {}
            case EngagementArchived e -> {}
            default -> throw new IllegalStateException("Unexpected value: " + event);
        }
    }
}
