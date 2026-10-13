package com.example.engagementupdate.events;

import com.example.engagementupdate.domain.EngagementId;
import com.example.engagementupdate.domain.FirmId;
import java.time.Instant;
import java.util.Objects;

/** Validation shared by the fields every engagement event carries. */
final class EventChecks {

    private EventChecks() {}

    static void common(FirmId firmId, EngagementId engagementId, long seq, String actorId, Instant occurredAt) {
        Objects.requireNonNull(firmId);
        Objects.requireNonNull(engagementId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(occurredAt);
        if (seq <= 0) {
            throw new IllegalArgumentException("seq must be positive: " + seq);
        }
    }
}
