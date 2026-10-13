package com.example.engagementupdate.domain;

public record FirmId(String value) {
    public FirmId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("FirmId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
