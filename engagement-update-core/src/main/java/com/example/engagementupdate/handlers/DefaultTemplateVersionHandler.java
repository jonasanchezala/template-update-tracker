package com.example.engagementupdate.handlers;

import com.example.engagementupdate.events.TemplateVersionPublished;
import com.example.engagementupdate.store.LatestVersionStore;
import java.util.Objects;

/** R5. */
public final class DefaultTemplateVersionHandler implements TemplateVersionHandler {

    private final LatestVersionStore latestVersions;

    public DefaultTemplateVersionHandler(LatestVersionStore latestVersions) {
        this.latestVersions = Objects.requireNonNull(latestVersions);
    }

    @Override
    public boolean handle(TemplateVersionPublished event) {
        return latestVersions.raiseTo(event.templateId(), event.version());
    }
}
