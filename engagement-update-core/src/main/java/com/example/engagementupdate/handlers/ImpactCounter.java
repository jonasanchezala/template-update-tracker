package com.example.engagementupdate.handlers;

import com.example.engagementupdate.domain.FirmId;
import com.example.engagementupdate.domain.TemplateId;
import com.example.engagementupdate.domain.Version;
import java.util.Map;

/** Worker side: counts across every firm (R10). */
public interface ImpactCounter {

    /**
     * For every firm with at least one engagement on the template, the number of ACTIVE engagements
     * that publishing {@code version} would leave to review. Firms with none appear with 0 (R8).
     */
    Map<FirmId, Long> count(TemplateId templateId, Version version);
}
