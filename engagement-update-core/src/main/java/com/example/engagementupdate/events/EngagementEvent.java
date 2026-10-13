package com.example.engagementupdate.events;

import com.example.engagementupdate.domain.EngagementId;
import com.example.engagementupdate.domain.FirmId;
import java.time.Instant;

/**
 * Every engagement event carries the firm, the engagement, a per-engagement sequence number that
 * only ever increases, the actor, and the time it happened.
 */
public interface EngagementEvent {

    FirmId firmId();

    EngagementId engagementId();

    long seq();

    String actorId();

    Instant occurredAt();
}
