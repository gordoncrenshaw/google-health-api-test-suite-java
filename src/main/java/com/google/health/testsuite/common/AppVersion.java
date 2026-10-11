package com.google.health.testsuite.common;

/**
 * Single source of truth for application versioning.
 * Build-tool independent (independent of Maven, Gradle, or other build systems).
 */
public final class AppVersion {

    /**
     * Current application semantic version (MAJOR.MINOR.PATCH).
     */
    public static final String VERSION = "1.0.0";

    /**
     * Target Google Health API version.
     */
    public static final String API_TARGET = "v4";

    /**
     * Release date of the current build.
     */
    public static final String RELEASE_DATE = "2026-10-10";

    /**
     * Application title.
     */
    public static final String APP_NAME = "Google Health API Test Suite";

    private AppVersion() {
        // Utility class
    }

    public static String getVersion() {
        return VERSION;
    }

    public static String getFormattedVersion() {
        return "v" + VERSION;
    }
}
