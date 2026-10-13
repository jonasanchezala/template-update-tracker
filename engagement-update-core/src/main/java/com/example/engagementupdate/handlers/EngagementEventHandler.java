package com.example.engagementupdate.handlers;

import com.example.engagementupdate.events.EngagementEvent;

/** Worker side: cross-tenant, fed by the queue. */
public interface EngagementEventHandler {

    HandleOutcome handle(EngagementEvent event);
}
