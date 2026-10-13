package com.example.engagementupdate.domain;

public record TemplateId(String value) {
    public TemplateId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TemplateId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
