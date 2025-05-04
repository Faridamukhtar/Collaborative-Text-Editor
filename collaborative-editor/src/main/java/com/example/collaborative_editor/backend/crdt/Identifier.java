package com.example.collaborative_editor.backend.crdt;

import java.util.Objects;

public class Identifier implements Comparable<Identifier> {
    private final int digit;
    private final String siteId;

    public Identifier(int digit, String siteId) {
        this.digit = digit;
        this.siteId = siteId;
    }

    public int getDigit() {
        return digit;
    }

    public String getSiteId() {
        return siteId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Identifier that = (Identifier) o;
        return digit == that.digit && Objects.equals(siteId, that.siteId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(digit, siteId);
    }

    @Override
    public int compareTo(Identifier o) {
        if (this.digit != o.digit) {
            return Integer.compare(this.digit, o.digit);
        }
        return this.siteId.compareTo(o.siteId);
    }
}