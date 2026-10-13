package com.example.engagementupdate.handlers;

import com.example.engagementupdate.events.TemplateVersionPublished;

/** Worker side. */
public interface TemplateVersionHandler {

    /** True if the template's latest version changed. */
    boolean handle(TemplateVersionPublished event);
}
