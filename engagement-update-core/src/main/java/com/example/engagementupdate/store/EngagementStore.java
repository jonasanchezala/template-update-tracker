package com.example.engagementupdate.store;

import com.example.engagementupdate.domain.DecisionRecord;
import com.example.engagementupdate.domain.EngagementId;
import com.example.engagementupdate.domain.EngagementRecord;
import com.example.engagementupdate.domain.FirmId;
import com.example.engagementupdate.domain.TemplateId;
import java.util.List;
import java.util.Optional;

/** Operations mirror the conditional writes the real database will offer. */
public interface EngagementStore {

    Optional<EngagementRecord> find(FirmId firmId, EngagementId engagementId);

    /** Writes only if no record exists for the firm and engagement. R6, R1a. */
    boolean putIfAbsent(EngagementRecord record);

    /** Writes only if a record exists and {@code updated.lastSeq()} is greater than the stored one. R1. */
    boolean replaceIfSeqNewer(EngagementRecord updated);

    List<EngagementRecord> findByFirm(FirmId firmId);

    /** Cross-tenant: for the worker side only (R10). */
    List<EngagementRecord> findByTemplate(TemplateId templateId);

    void appendDecision(DecisionRecord decision);

    /** Oldest first (R9). */
    List<DecisionRecord> decisionsByFirm(FirmId firmId);
}
