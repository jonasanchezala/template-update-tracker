package com.example.engagementupdate.events;

import com.example.engagementupdate.domain.EngagementId;
import com.example.engagementupdate.domain.FirmId;
import com.example.engagementupdate.domain.TemplateId;
import com.example.engagementupdate.domain.Version;
import java.time.Instant;
import java.util.Objects;

/** {@code toVersion} is the version declined through. */
public record UpdateDeclined(
        FirmId firmId,
        EngagementId engagementId,
        long seq,
        String actorId,
        Instant occurredAt,
        TemplateId templateId,
        Version fromVersion,
        Version toVersion)
        implements EngagementEvent {

    public UpdateDeclined {
        EventChecks.common(firmId, engagementId, seq, actorId, occurredAt);
        Objects.requireNonNull(templateId);
        Objects.requireNonNull(fromVersion);
        Objects.requireNonNull(toVersion);
    }
}
