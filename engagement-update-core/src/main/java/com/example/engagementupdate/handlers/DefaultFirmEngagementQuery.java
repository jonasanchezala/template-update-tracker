package com.example.engagementupdate.handlers;

import com.example.engagementupdate.domain.DecisionRecord;
import com.example.engagementupdate.domain.EngagementView;
import com.example.engagementupdate.domain.FirmId;
import com.example.engagementupdate.domain.PendingRule;
import com.example.engagementupdate.domain.Version;
import com.example.engagementupdate.store.EngagementStore;
import com.example.engagementupdate.store.LatestVersionStore;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** R7 and R10. Archived engagements are listed, never pending. */
public final class DefaultFirmEngagementQuery implements FirmEngagementQuery {

    private final EngagementStore engagements;
    private final LatestVersionStore latestVersions;

    public DefaultFirmEngagementQuery(EngagementStore engagements, LatestVersionStore latestVersions) {
        this.engagements = Objects.requireNonNull(engagements);
        this.latestVersions = Objects.requireNonNull(latestVersions);
    }

    @Override
    public List<EngagementView> engagementsFor(FirmId firmId) {
        return engagements.findByFirm(firmId).stream()
                .map(record -> {
                    Optional<Version> latest = latestVersions.latest(record.templateId());
                    return new EngagementView(
                            record.engagementId(),
                            record.templateId(),
                            record.appliedVersion(),
                            record.declinedThroughVersion(),
                            latest,
                            PendingRule.isPending(
                                    record.status(), latest, record.appliedVersion(), record.declinedThroughVersion()));
                })
                .sorted(Comparator.comparing(view -> view.engagementId().value()))
                .toList();
    }

    @Override
    public List<DecisionRecord> decisionHistoryFor(FirmId firmId) {
        return engagements.decisionsByFirm(firmId);
    }
}
