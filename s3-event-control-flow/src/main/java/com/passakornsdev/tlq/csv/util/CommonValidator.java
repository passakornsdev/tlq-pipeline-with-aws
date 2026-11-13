package com.passakornsdev.tlq.csv.util;

public class CommonValidator {
    private CommonValidator() {}

    public static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
