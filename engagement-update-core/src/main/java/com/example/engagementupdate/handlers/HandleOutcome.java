package com.example.engagementupdate.handlers;

public enum HandleOutcome {
    /** The event changed state: a record was created or updated. */
    APPLIED,
    /** R1: the event's seq was not newer than the record's. Nothing changed. */
    IGNORED_STALE,
    /** R1a: an archive for an engagement we have no record of. Nothing changed. */
    IGNORED_UNKNOWN
}
