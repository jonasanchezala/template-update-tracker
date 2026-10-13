package com.example.engagementupdate.handlers;

import com.example.engagementupdate.events.BackfillSnapshot;

/** Worker side. */
public interface BackfillSeeder {

    SeedOutcome seed(BackfillSnapshot snapshot);
}
