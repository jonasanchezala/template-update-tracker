package com.example.engagementupdate.domain;

import java.time.Instant;
import java.util.Objects;

public record DecisionRecord(
        FirmId firmId,
        EngagementId engagementId,
        TemplateId templateId,
        DecisionOutcome outcome,
        Version fromVersion,
        Version toVersion,
        String actorId,
        Instant occurredAt) {

    public DecisionRecord {
        Objects.requireNonNull(firmId);
        Objects.requireNonNull(engagementId);
        Objects.requireNonNull(templateId);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(fromVersion);
        Objects.requireNonNull(toVersion);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(occurredAt);
    }
}
