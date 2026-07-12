package com.votewithblock.gateway.util;

public final class SafeLog {

    private SafeLog() {}

    public static String id(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        String clean = value.replaceAll("[\\r\\n\\t]", "_");
        if (clean.length() <= 4) {
            return "****";
        }
        return clean.substring(0, Math.min(3, clean.length())) + "***" + clean.substring(clean.length() - 2);
    }
}
