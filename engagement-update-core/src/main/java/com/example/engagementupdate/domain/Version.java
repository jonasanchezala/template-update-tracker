package com.example.engagementupdate.domain;

public record Version(int value) implements Comparable<Version> {
    public Version {
        if (value <= 0) {
            throw new IllegalArgumentException("Version must be positive: " + value);
        }
    }

    public static Version of(int value) {
        return new Version(value);
    }

    public static Version max(Version a, Version b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    @Override
    public int compareTo(Version other) {
        return Integer.compare(value, other.value);
    }

    @Override
    public String toString() {
        return "v" + value;
    }
}
