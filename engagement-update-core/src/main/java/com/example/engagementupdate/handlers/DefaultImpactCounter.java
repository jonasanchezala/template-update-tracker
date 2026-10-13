package com.example.engagementupdate.handlers;

import com.example.engagementupdate.domain.FirmId;
import com.example.engagementupdate.domain.PendingRule;
import com.example.engagementupdate.domain.TemplateId;
import com.example.engagementupdate.domain.Version;
import com.example.engagementupdate.store.EngagementStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** R8. */
public final class DefaultImpactCounter implements ImpactCounter {

    private final EngagementStore store;

    public DefaultImpactCounter(EngagementStore store) {
        this.store = Objects.requireNonNull(store);
    }

    @Override
    public Map<FirmId, Long> count(TemplateId templateId, Version version) {
        Map<FirmId, Long> counts = new HashMap<>();
        store.findByTemplate(templateId).forEach(record -> counts.merge(
                record.firmId(), PendingRule.isAffectedBy(record, version) ? 1L : 0L, Long::sum));
        return counts;
    }
}
