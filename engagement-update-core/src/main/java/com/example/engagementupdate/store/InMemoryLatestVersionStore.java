package com.example.engagementupdate.store;

import com.example.engagementupdate.domain.TemplateId;
import com.example.engagementupdate.domain.Version;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class InMemoryLatestVersionStore implements LatestVersionStore {

    private final ConcurrentHashMap<TemplateId, Version> latest = new ConcurrentHashMap<>();

    @Override
    public Optional<Version> latest(TemplateId templateId) {
        return Optional.ofNullable(latest.get(templateId));
    }

    @Override
    public boolean raiseTo(TemplateId templateId, Version version) {
        AtomicBoolean changed = new AtomicBoolean(false);
        latest.compute(templateId, (id, current) -> {
            if (current == null || version.compareTo(current) > 0) {
                changed.set(true);
                return version;
            }
            return current;
        });
        return changed.get();
    }
}
