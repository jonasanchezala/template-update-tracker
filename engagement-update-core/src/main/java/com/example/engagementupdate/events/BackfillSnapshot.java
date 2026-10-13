package com.example.engagementupdate.events;

import com.example.engagementupdate.domain.EngagementId;
import com.example.engagementupdate.domain.EngagementStatus;
import com.example.engagementupdate.domain.FirmId;
import com.example.engagementupdate.domain.TemplateId;
import com.example.engagementupdate.domain.Version;
import java.util.Objects;

/** A snapshot read from one engagement load. */
public record BackfillSnapshot(
        FirmId firmId, EngagementId engagementId, TemplateId templateId, Version version, EngagementStatus status) {
    public BackfillSnapshot {
        Objects.requireNonNull(firmId);
        Objects.requireNonNull(engagementId);
        Objects.requireNonNull(templateId);
        Objects.requireNonNull(version);
        Objects.requireNonNull(status);
    }
}
