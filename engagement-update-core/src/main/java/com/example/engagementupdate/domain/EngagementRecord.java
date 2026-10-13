package com.example.engagementupdate.domain;

import java.util.Objects;
import java.util.Optional;

/** */
public record EngagementRecord(
        FirmId firmId,
        EngagementId engagementId,
        TemplateId templateId,
        Version appliedVersion,
        Optional<Version> declinedThroughVersion,
        EngagementStatus status,
        long lastSeq,
        RecordSource source) {

    public EngagementRecord {
        Objects.requireNonNull(firmId);
        Objects.requireNonNull(engagementId);
        Objects.requireNonNull(templateId);
        Objects.requireNonNull(appliedVersion);
        Objects.requireNonNull(declinedThroughVersion);
        Objects.requireNonNull(status);
        Objects.requireNonNull(source);
        if (lastSeq < 0) {
            throw new IllegalArgumentException("lastSeq must not be negative: " + lastSeq);
        }
    }
}
