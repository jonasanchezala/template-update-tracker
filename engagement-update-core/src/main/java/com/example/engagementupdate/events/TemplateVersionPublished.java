package com.example.engagementupdate.events;

import com.example.engagementupdate.domain.TemplateId;
import com.example.engagementupdate.domain.Version;
import java.time.Instant;
import java.util.Objects;

/** Carries no tenant data and no sequence number: only the max matters (R5). */
public record TemplateVersionPublished(TemplateId templateId, Version version, Instant publishedAt) {
    public TemplateVersionPublished {
        Objects.requireNonNull(templateId);
        Objects.requireNonNull(version);
        Objects.requireNonNull(publishedAt);
    }
}
