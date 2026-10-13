package com.example.engagementupdate.events;

import com.example.engagementupdate.domain.EngagementId;
import com.example.engagementupdate.domain.FirmId;
import com.example.engagementupdate.domain.TemplateId;
import com.example.engagementupdate.domain.Version;
import java.time.Instant;
import java.util.Objects;

public record UpdateApplied(
        FirmId firmId,
        EngagementId engagementId,
        long seq,
        String actorId,
        Instant occurredAt,
        TemplateId templateId,
        Version fromVersion,
        Version toVersion)
        implements EngagementEvent {

    public UpdateApplied {
        EventChecks.common(firmId, engagementId, seq, actorId, occurredAt);
        Objects.requireNonNull(templateId);
        Objects.requireNonNull(fromVersion);
        Objects.requireNonNull(toVersion);
    }
}
