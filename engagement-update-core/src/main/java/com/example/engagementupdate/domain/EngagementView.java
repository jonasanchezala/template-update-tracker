package com.example.engagementupdate.domain;

import java.util.Objects;
import java.util.Optional;

/** One row of a firm's engagement list. */
public record EngagementView(
        EngagementId engagementId,
        TemplateId templateId,
        Version appliedVersion,
        Optional<Version> declinedThroughVersion,
        Optional<Version> latestVersion,
        boolean pending) {

    public EngagementView {
        Objects.requireNonNull(engagementId);
        Objects.requireNonNull(templateId);
        Objects.requireNonNull(appliedVersion);
        Objects.requireNonNull(declinedThroughVersion);
        Objects.requireNonNull(latestVersion);
    }
}
