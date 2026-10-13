package com.example.engagementupdate.domain;

import java.util.Optional;

/**
 * R7, and the comparison R8 shares with it. Pure functions, no storage.
 *
 * <p>The baseline is the highest version the firm has already dealt with: the applied version, or
 * the version it declined through if that is higher. A decline never moves the applied version
 * (A-2), only this baseline.
 */
public final class PendingRule {

    private PendingRule() {}

    public static int baseline(Version applied, Optional<Version> declinedThrough) {
        return Math.max(applied.value(), declinedThrough.map(Version::value).orElse(0));
    }

    /** R7: pending exactly when ACTIVE, the latest version is known, and it is beyond the baseline. */
    public static boolean isPending(
            EngagementStatus status,
            Optional<Version> latest,
            Version applied,
            Optional<Version> declinedThrough) {
        return status == EngagementStatus.ACTIVE
                && latest.isPresent()
                && latest.get().value() > baseline(applied, declinedThrough);
    }

    /** R8: would publishing {@code version} leave this engagement with an update to review? */
    public static boolean isAffectedBy(EngagementRecord record, Version version) {
        return record.status() == EngagementStatus.ACTIVE
                && version.value() > baseline(record.appliedVersion(), record.declinedThroughVersion());
    }
}
