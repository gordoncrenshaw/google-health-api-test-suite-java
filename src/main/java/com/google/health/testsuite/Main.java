package com.google.health.testsuite;

import com.google.health.testsuite.auth.OAuthService;
import com.google.health.testsuite.client.HealthApiClient;
import com.google.health.testsuite.config.ConfigManager;
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

                Options:
                  --menu, -m                Launch interactive terminal menu
                  --web, -w [port]          Start embedded web server and host Web UX (default port: 8080)
                  --script, -s <path>       Execute automated test script file
                  --help, -h                Show this help screen
                """);
    }
}
