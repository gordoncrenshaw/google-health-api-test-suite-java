package com.google.health.testsuite;

import com.google.health.testsuite.auth.BrowserUtil;
import com.google.health.testsuite.auth.LocalOAuthReceiver;
import com.google.health.testsuite.auth.OAuthService;
import com.google.health.testsuite.client.HealthApiClient;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.config.DataTypeRegistry;
import com.google.health.testsuite.runner.CliMenuRunner;
import com.google.health.testsuite.runner.ScriptRunner;
import com.google.health.testsuite.runner.SuiteExecutionEngine;
import com.google.health.testsuite.server.WebServer;

import java.io.File;

/**
 * Main entry point for the Google Health API Test Suite.
 * Supports 3 execution modes:
 *   1. Command Line Menu (interactive CLI)
 *   2. Web UX using JavaScript/HTML
 *   3. Non-interactive Script file runner
 */
public class Main {

    public static void main(String[] args) {
        // 1. Initialize core system components
        ConfigManager configManager = new ConfigManager();
        DataTypeRegistry dataTypeRegistry = new DataTypeRegistry();
        OAuthService oAuthService = new OAuthService(configManager);
        HealthApiClient apiClient = new HealthApiClient(configManager, oAuthService);
        SuiteExecutionEngine engine = new SuiteExecutionEngine(configManager, dataTypeRegistry, apiClient);

        // Check for --mock flag across arguments
        for (String arg : args) {
            if ("--mock".equalsIgnoreCase(arg) || "-mock".equalsIgnoreCase(arg)) {
                configManager.getPreferences().setMockMode(true);
            }
        }

        // 2. Parse command line arguments
        if (args.length == 0 || args[0].equals("--menu") || args[0].equals("-m")) {
            // Mode 1: Command Line Menu
            CliMenuRunner menu = new CliMenuRunner(engine, oAuthService);
            menu.run();

        } else if (args[0].equals("--web") || args[0].equals("-w")) {
            // Mode 2: Web UX (HTML / JavaScript)
            int port = 8080;
            if (args.length > 1) {
                try {
                    port = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    System.err.println("Warning: Invalid port '" + args[1] + "', falling back to 8080");
                }
            }
            try {
                WebServer webServer = new WebServer(port, engine, oAuthService);
                webServer.start();
                // Keep server thread alive
                Thread.currentThread().join();
            } catch (Exception e) {
                System.err.println("Failed to start web server: " + e.getMessage());
                System.exit(1);
            }

        } else if (args[0].equals("--script") || args[0].equals("-s")) {
            // Mode 3: Through a script file
            if (args.length < 2) {
                System.err.println("Error: Missing script file argument.");
                System.err.println("Usage: java -jar target/health-api-testsuite.jar --script <path-to-script.yaml>");
                System.exit(1);
            }
            File scriptFile = new File(args[1]);
            ScriptRunner runner = new ScriptRunner(engine);
            int exitCode = runner.runScript(scriptFile);
            System.exit(exitCode);

        } else if (args[0].equals("--auth") || args[0].equals("-a")) {
            // Dedicated option to authorize connection
            runDirectAuthorization(configManager, oAuthService);

        } else if (args[0].equals("--help") || args[0].equals("-h")) {
            printHelp();

        } else {
            // If argument is directly a file path ending in .yaml or .json, run as script
            File potentialScript = new File(args[0]);
            if (potentialScript.exists() && (args[0].endsWith(".yaml") || args[0].endsWith(".json"))) {
                ScriptRunner runner = new ScriptRunner(engine);
                int exitCode = runner.runScript(potentialScript);
                System.exit(exitCode);
            } else {
                System.err.println("Unknown command option: " + args[0]);
                printHelp();
                System.exit(1);
            }
        }
    }

    private static void runDirectAuthorization(ConfigManager configManager, OAuthService oAuthService) {
        Preferences prefs = configManager.getPreferences();
        System.out.println("\n\u001B[36m\u001B[1m========================================================================\u001B[0m");
        System.out.println("\u001B[36m\u001B[1m Google Health API - Direct Authorization Flow\u001B[0m");
        System.out.println("\u001B[36m\u001B[1m========================================================================\u001B[0m\n");

        if (!prefs.isConfigured() && !prefs.isMockMode()) {
            System.err.println("Error: Client ID or Client Secret is missing in config/preferences.yaml.");
            System.exit(1);
        }

        LocalOAuthReceiver receiver = null;
        try {
            receiver = LocalOAuthReceiver.fromRedirectUri(prefs.getRedirectUri(), oAuthService);
            receiver.start();
            System.out.println("Local OAuth callback receiver is listening on: \u001B[33m" + prefs.getRedirectUri() + "\u001B[0m");

            String authUrl = oAuthService.buildAuthorizationUrl("auth_flow_" + System.currentTimeMillis());
            System.out.println("\nOpening your browser to authorize access to Google Health API...");
            System.out.println("If the browser does not open automatically, visit this URL:");
            System.out.println("\u001B[34m\u001B[4m" + authUrl + "\u001B[0m\n");

            BrowserUtil.openBrowser(authUrl);

            System.out.println("Waiting up to 180 seconds for browser callback...");
            boolean success = receiver.waitForCallback(180);
            if (success) {
                System.out.println("\n\u001B[32m\u001B[1m[SUCCESS] Authorization successful!\u001B[0m");
                System.out.println("Credentials have been saved to the Credential store.");
                System.out.println("You can now run tests via CLI, Web UX, or Script runner.\n");
                System.exit(0);
            } else {
                System.err.println("\n\u001B[31m[FAILED] Authorization timed out or failed.\u001B[0m");
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error during authorization flow: " + e.getMessage());
            System.exit(1);
        } finally {
            if (receiver != null) receiver.stop();
        }
    }

    private static void printHelp() {
        System.out.println("""
                Google Health API Test Suite (v4)
                ---------------------------------
                Usage:
                  1. Command Line Menu (Default):
                     mvn exec:java
                     or: java -jar target/health-api-testsuite.jar --menu

                  2. Web UX (JavaScript / HTML):
                     java -jar target/health-api-testsuite.jar --web [port]
                     (Example: java -jar target/health-api-testsuite.jar --web 8080)

                  3. Run through a script file:
                     java -jar target/health-api-testsuite.jar --script <path-to-script>
                     (Example: java -jar target/health-api-testsuite.jar --script scripts/sample_suite.yaml)

                  4. Authorize Connection directly:
                     java -jar target/health-api-testsuite.jar --auth
                     or: ./run.sh auth

                Options:
                  --menu, -m                Launch interactive terminal menu
                  --web, -w [port]          Start embedded web server and host Web UX (default port: 8080)
                  --script, -s <path>       Execute automated test script file
                  --auth, -a                Run direct authorization flow to connect to Google Health API
                  --mock                    Enable mock/simulation mode without live credentials
                  --help, -h                Show this help screen
                """);
    }
}
