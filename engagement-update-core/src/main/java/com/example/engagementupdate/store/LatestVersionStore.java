package com.example.engagementupdate.store;

import com.example.engagementupdate.domain.TemplateId;
import com.example.engagementupdate.domain.Version;
import java.util.Optional;

public interface LatestVersionStore {

    Optional<Version> latest(TemplateId templateId);

    /** Sets the latest version to {@code max(current, version)}. True if it changed. R5. */
    boolean raiseTo(TemplateId templateId, Version version);
}
