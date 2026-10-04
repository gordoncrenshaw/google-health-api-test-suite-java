package com.google.health.testsuite.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Utility for opening system browsers on macOS, Linux, and Windows.
 */
public class BrowserUtil {

    private static final Logger logger = LoggerFactory.getLogger(BrowserUtil.class);

    public static boolean openBrowser(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        // 1. Try Desktop API
        try {
            if (java.awt.Desktop.isDesktopSupported() &&
                java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (Throwable ignored) {
        }

        // 2. Fallback to OS command line
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("mac") || os.contains("darwin")) {
                new ProcessBuilder("open", url).start();
                return true;
            } else if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                return true;
            } else {
                new ProcessBuilder("xdg-open", url).start();
                return true;
            }
        } catch (Throwable e) {
            logger.warn("Could not open system browser automatically: {}", e.getMessage());
            return false;
        }
    }
}
