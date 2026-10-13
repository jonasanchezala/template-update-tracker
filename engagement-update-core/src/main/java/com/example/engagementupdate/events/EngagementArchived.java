package com.example.engagementupdate.events;

import com.example.engagementupdate.domain.EngagementId;
import com.example.engagementupdate.domain.FirmId;
import java.time.Instant;

public record EngagementArchived(FirmId firmId, EngagementId engagementId, long seq, String actorId, Instant occurredAt)
        implements EngagementEvent {

    public EngagementArchived {
        EventChecks.common(firmId, engagementId, seq, actorId, occurredAt);
    }
}
