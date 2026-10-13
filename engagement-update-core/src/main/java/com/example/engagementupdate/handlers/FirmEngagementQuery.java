package com.example.engagementupdate.handlers;

import com.example.engagementupdate.domain.DecisionRecord;
import com.example.engagementupdate.domain.EngagementView;
import com.example.engagementupdate.domain.FirmId;
import java.util.List;

/** API side: every method takes the firm and can only return that firm's data (R10). */
public interface FirmEngagementQuery {

    List<EngagementView> engagementsFor(FirmId firmId);

    /** Oldest first. */
    List<DecisionRecord> decisionHistoryFor(FirmId firmId);
}
