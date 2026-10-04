package com.google.health.testsuite.runner;

import com.google.health.testsuite.auth.BrowserUtil;
import com.google.health.testsuite.auth.LocalOAuthReceiver;
import com.google.health.testsuite.auth.OAuthService;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.DataTypeRegistry;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.config.UserAuthorization;
import com.google.health.testsuite.model.ApiResponse;
import com.google.health.testsuite.model.DataTypeDefinition;
import com.google.health.testsuite.model.TestResult;

import java.io.File;
import java.util.*;

/**
 * Mode 1: Interactive Command-Line Menu for running the Google Health API Test Suite.
 */
public class CliMenuRunner {

    // ANSI Colors for terminal output
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String BLUE = "\u001B[34m";
    private static final String MAGENTA = "\u001B[35m";

    private final SuiteExecutionEngine engine;
    private final ConfigManager configManager;
    private final DataTypeRegistry dataTypeRegistry;
    private final OAuthService oAuthService;
    private final Scanner scanner;

    public CliMenuRunner(SuiteExecutionEngine engine, OAuthService oAuthService) {
        this.engine = engine;
        this.configManager = engine.getConfigManager();
        this.dataTypeRegistry = engine.getDataTypeRegistry();
        this.oAuthService = oAuthService;
        this.scanner = new Scanner(System.in);
    }

    public void run() {
        printBanner();

        boolean running = true;
        while (running) {
            printMainMenu();
            System.out.print(BOLD + "Select an option [0-10]: " + RESET);
            String input = scanner.nextLine().trim();

            switch (input) {
                case "1" -> showPreferencesMenu();
                case "2" -> showTokenStatus();
                case "3" -> startAuthorizationFlow();
                case "4" -> refreshAccessTokenManually();
                case "5" -> listSupportedDataTypes();
                case "6" -> runSingleDataTypeTest();
                case "7" -> runFullTestSuite();
                case "8" -> runProfileAndDeviceTests();
                case "9" -> runScriptFilePrompt();
                case "10" -> toggleMockMode();
                case "0", "exit", "quit" -> {
                    System.out.println(CYAN + "\nExiting Google Health API Test Suite. Goodbye!\n" + RESET);
                    running = false;
                }
                default -> System.out.println(RED + "Invalid option. Please choose between 0 and 10." + RESET);
            }

            if (running) {
                System.out.println("\nPress [ENTER] to return to main menu...");
                scanner.nextLine();
            }
        }
    }

    private void printBanner() {
        System.out.println(CYAN + BOLD + """
                ========================================================================
                   ____                   _         _   _            _ _   _     
                  / ___| ___   ___   __ _| | ___   | | | | ___  __ _| | |_| |__  
                 | |  _ / _ \\ / _ \\ / _` | |/ _ \\  | |_| |/ _ \\/ _` | | __| '_ \\ 
                 | |_| | (_) | (_) | (_| | |  __/  |  _  |  __/ (_| | | |_| | | |
                  \\____|\\___/ \\___/ \\__, |_|\\___|  |_| |_|\\___|\\__,_|_|\\__|_| |_|
                                    |___/        API TEST SUITE (v4)             
                ========================================================================
                """ + RESET);
    }

    private void printMainMenu() {
        Preferences prefs = configManager.getPreferences();
        UserAuthorization auth = configManager.getUserAuthorization();
        String effectiveUserId = !prefs.getHealthUserId().isEmpty() ? prefs.getHealthUserId() : auth.getHealthUserID();

        System.out.println(BOLD + "\n[ MAIN MENU ]" + RESET);
        System.out.printf(" Mode: %s | HealthUserID: %s | Token: %s (%ds remaining)\n",
                (prefs.isMockMode() ? YELLOW + "MOCK/SIMULATION" : GREEN + "LIVE GOOGLE API") + RESET,
                CYAN + effectiveUserId + RESET,
                (auth.hasAccessToken() ? (auth.isExpired() ? RED + "EXPIRED" : GREEN + "VALID") : RED + "NONE") + RESET,
                auth.getRemainingSeconds());
        System.out.println("------------------------------------------------------------------------");
        System.out.println(" 1. Preferences & Auth Menu (Dashboard, Auth, getIdentity, getDevices)");
        System.out.println(" 2. View Authorization & Token Details");
        System.out.println(" 3. Authorize with Google (OAuth 2.0 Web Callback / Manual Code)");
        System.out.println(" 4. Refresh Access Token Now (Automatic Rotation & Save)");
        System.out.println(" 5. List Supported Data Types (From datatypes.yaml)");
        System.out.println(" 6. Run Single Data Type API Test (List, Get, Create, Rollup)");
        System.out.println(" 7. Run Comprehensive Test Suite Across All Data Types");
        System.out.println(" 8. Test Identity, Profile & Paired Devices Endpoints");
        System.out.println(" 9. Run a Test Script File (Mode 3 Script Runner)");
        System.out.printf(" 10. Toggle Mock/Live Mode (Current: %s)\n", prefs.isMockMode() ? "MOCK" : "LIVE");
        System.out.println(" 0. Exit");
        System.out.println("------------------------------------------------------------------------");
    }

    private void showPreferencesMenu() {
        boolean inPrefs = true;
        while (inPrefs) {
            Preferences prefs = configManager.getPreferences();
            UserAuthorization auth = configManager.getUserAuthorization();

            System.out.println(CYAN + BOLD + "\n========================================================================" + RESET);
            System.out.println(CYAN + BOLD + "             PREFERENCES & AUTHORIZATION DASHBOARD                      " + RESET);
            System.out.println(CYAN + BOLD + "========================================================================" + RESET);
            System.out.println(BOLD + "[ Preferences Configuration (config/preferences.yaml) ]" + RESET);
            System.out.println(" Client ID:      " + (prefs.getClientId().isEmpty() ? "(Not configured)" : prefs.getClientId()));
            System.out.println(" Client Secret:  " + (prefs.getClientSecret().isEmpty() ? "(Not configured)" : "********" + (prefs.getClientSecret().length() > 4 ? prefs.getClientSecret().substring(prefs.getClientSecret().length() - 4) : "")));
            System.out.println(" Health User ID: " + (prefs.getHealthUserId().isEmpty() ? YELLOW + "(None - run getIdentity to auto-populate)" + RESET : GREEN + prefs.getHealthUserId() + RESET));
            System.out.println(" Redirect URI:   " + prefs.getRedirectUri());
            System.out.println(" API Base URL:   " + prefs.getApiBaseUrl());
            System.out.println(" Default User:   " + prefs.getDefaultUserId());
            System.out.println(" Mock Mode:      " + (prefs.isMockMode() ? YELLOW + "ENABLED" : GREEN + "DISABLED (Live API)") + RESET);

            System.out.println(BOLD + "\n[ User Authorization Status (config/userAuthorization.yaml) ]" + RESET);
            System.out.println(" Health User ID: " + auth.getHealthUserID());
            System.out.println(" Access Token:   " + (auth.hasAccessToken() ? (auth.isExpired() ? RED + "EXPIRED" : GREEN + "VALID") : RED + "NONE") + RESET + " (" + auth.getRemainingSeconds() + "s remaining)");
            System.out.println(" Refresh Token:  " + (auth.hasRefreshToken() ? GREEN + "CONFIGURED (Auto-refresh active)" : RED + "NONE") + RESET);
            System.out.println(" Auto-Refresh:   " + GREEN + "Active on 401 or token expiration" + RESET);

            System.out.println(BOLD + "\n[ Actions ]" + RESET);
            System.out.println(" 1. Authorize Connection with Google (OAuth 2.0 Web Callback / Manual)");
            System.out.println(" 2. Force Refresh Access Token Now");
            System.out.println(" 3. Call getIdentity Endpoint (GET /v4/users/{userId}/identity)");
            System.out.println(" 4. Call getDevices Endpoint (GET /v4/users/{userId}/pairedDevices)");
            System.out.println(" 5. Call getProfile Endpoint (GET /v4/users/{userId}/profile)");
            System.out.println(" 6. Edit Preferences (Client ID, Secret, Health User ID, Redirect URI)");
            System.out.println(" 7. View Stored Scopes");
            System.out.println(" 0. Return to Main Menu");
            System.out.println("------------------------------------------------------------------------");
            System.out.print(BOLD + "Select option [0-7]: " + RESET);
            String opt = scanner.nextLine().trim();

            switch (opt) {
                case "1" -> startAuthorizationFlow();
                case "2" -> refreshAccessTokenManually();
                case "3" -> callGetIdentityEndpoint();
                case "4" -> callGetDevicesEndpoint();
                case "5" -> callGetProfileEndpoint();
                case "6" -> editPreferencesPrompt();
                case "7" -> {
                    System.out.println(CYAN + "\nConfigured OAuth Scopes (" + prefs.getScopes().size() + "):" + RESET);
                    for (String s : prefs.getScopes()) System.out.println(" - " + s);
                }
                case "0", "back", "exit" -> inPrefs = false;
                default -> System.out.println(RED + "Invalid option." + RESET);
            }

            if (inPrefs) {
                System.out.println("\nPress [ENTER] to continue in Preferences Menu...");
                scanner.nextLine();
            }
        }
    }

    private void callGetIdentityEndpoint() {
        System.out.println(CYAN + BOLD + "\n--- Calling getIdentity Endpoint (GET /v4/users/{userId}/identity) ---" + RESET);
        Preferences prefs = configManager.getPreferences();
        String target = !prefs.getHealthUserId().isEmpty() ? prefs.getHealthUserId() : "me";
        System.out.println("Target user: " + target);

        ApiResponse resp = engine.getApiClient().getIdentity(null);

        System.out.println("\nHTTP Status: " + (resp.isSuccess() ? GREEN : RED) + resp.getStatusCode() + " " + resp.getStatusMessage() + RESET +
                " (" + resp.getLatencyMs() + "ms)");
        System.out.println("cURL:\n" + YELLOW + resp.getCurlCommand() + RESET);
        System.out.println("\nResponse Body (Pretty JSON):");
        System.out.println(resp.getBody());

        Preferences refreshed = configManager.getPreferences();
        if (!refreshed.getHealthUserId().isEmpty()) {
            System.out.println(GREEN + BOLD + "\n[INFO] Health User ID is stored in preferences: " + refreshed.getHealthUserId() + RESET);
        }
    }

    private void callGetDevicesEndpoint() {
        System.out.println(CYAN + BOLD + "\n--- Calling getDevices Endpoint (GET /v4/users/{userId}/pairedDevices) ---" + RESET);
        ApiResponse resp = engine.getApiClient().getDevices(null);

        System.out.println("\nHTTP Status: " + (resp.isSuccess() ? GREEN : RED) + resp.getStatusCode() + " " + resp.getStatusMessage() + RESET +
                " (" + resp.getLatencyMs() + "ms)");
        System.out.println("cURL:\n" + YELLOW + resp.getCurlCommand() + RESET);
        System.out.println("\nResponse Body (Pretty JSON):");
        System.out.println(resp.getBody());
    }

    private void callGetProfileEndpoint() {
        System.out.println(CYAN + BOLD + "\n--- Calling getProfile Endpoint (GET /v4/users/{userId}/profile) ---" + RESET);
        ApiResponse resp = engine.getApiClient().getProfile(null);

        System.out.println("\nHTTP Status: " + (resp.isSuccess() ? GREEN : RED) + resp.getStatusCode() + " " + resp.getStatusMessage() + RESET +
                " (" + resp.getLatencyMs() + "ms)");
        System.out.println("cURL:\n" + YELLOW + resp.getCurlCommand() + RESET);
        System.out.println("\nResponse Body (Pretty JSON):");
        System.out.println(resp.getBody());
    }

    private void editPreferencesPrompt() {
        Preferences prefs = configManager.getPreferences();
        System.out.println(CYAN + BOLD + "\n--- Edit Preferences ---" + RESET);
        System.out.println("(Press ENTER to leave current value unchanged)\n");

        System.out.print("Client ID [" + prefs.getClientId() + "]: ");
        String cid = scanner.nextLine().trim();
        if (!cid.isEmpty()) prefs.setClientId(cid);

        System.out.print("Client Secret [leave blank to keep unchanged]: ");
        String sec = scanner.nextLine().trim();
        if (!sec.isEmpty()) prefs.setClientSecret(sec);

        System.out.print("Health User ID [" + prefs.getHealthUserId() + "]: ");
        String hid = scanner.nextLine().trim();
        if (!hid.isEmpty()) prefs.setHealthUserId(hid);

        System.out.print("Redirect URI [" + prefs.getRedirectUri() + "]: ");
        String ruri = scanner.nextLine().trim();
        if (!ruri.isEmpty()) prefs.setRedirectUri(ruri);

        System.out.print("API Base URL [" + prefs.getApiBaseUrl() + "]: ");
        String url = scanner.nextLine().trim();
        if (!url.isEmpty()) prefs.setApiBaseUrl(url);

        configManager.savePreferences(prefs);
        System.out.println(GREEN + BOLD + "Preferences saved successfully to config/preferences.yaml!" + RESET);
    }

    private void showTokenStatus() {
        UserAuthorization auth = configManager.getUserAuthorization();
        System.out.println(CYAN + BOLD + "\n--- Authorization Status (config/userAuthorization.yaml) ---" + RESET);
        System.out.println(" Health User ID:   " + auth.getHealthUserID());
        System.out.println(" Has Access Token: " + (auth.hasAccessToken() ? GREEN + "YES" : RED + "NO") + RESET);
        System.out.println(" Has Refresh Token:" + (auth.hasRefreshToken() ? GREEN + "YES" : RED + "NO") + RESET);
        System.out.println(" Token Expired:    " + (auth.isExpired() ? RED + "YES (will auto-refresh on next request)" : GREEN + "NO") + RESET);
        System.out.println(" Remaining Sec:    " + auth.getRemainingSeconds() + " seconds");
        System.out.println(" Last Updated:     " + auth.getUpdatedAt());
        System.out.println(" Stored Scope:     " + auth.getScope());
    }

    private void startAuthorizationFlow() {
        Preferences prefs = configManager.getPreferences();
        System.out.println(CYAN + BOLD + "\n--- Google OAuth 2.0 Authorization ---" + RESET);

        if (!prefs.isConfigured() && !prefs.isMockMode()) {
            System.out.println(YELLOW + "WARNING: Client ID and Secret are not set in config/preferences.yaml!" + RESET);
            System.out.print("Would you like to enable Mock Mode for testing without credentials? [y/N]: ");
            String ans = scanner.nextLine().trim();
            if (ans.equalsIgnoreCase("y")) {
                prefs.setMockMode(true);
                configManager.savePreferences(prefs);
                System.out.println(GREEN + "Mock mode enabled! Simulating authorization..." + RESET);
                oAuthService.exchangeCodeForTokens("mock_auth_code");
                return;
            }
        }

        String authUrl = oAuthService.buildAuthorizationUrl("cli_state_" + System.currentTimeMillis());
        System.out.println("\nOpen this URL in your browser to authorize access to Google Health API:");
        System.out.println(BLUE + authUrl + RESET);

        System.out.println("\nOptions for receiving the authorization code:");
        System.out.println(" 1. Start automatic local HTTP callback server (Listens on " + prefs.getRedirectUri() + ")");
        System.out.println(" 2. Paste the authorization code manually from redirect URL");
        System.out.print("Select [1/2]: ");
        String choice = scanner.nextLine().trim();

        if ("1".equals(choice) || choice.isEmpty()) {
            LocalOAuthReceiver receiver = null;
            try {
                receiver = LocalOAuthReceiver.fromRedirectUri(prefs.getRedirectUri(), oAuthService);
                receiver.start();
                System.out.println("Launching your browser to authorize access to Google Health API...");
                BrowserUtil.openBrowser(authUrl);
                System.out.println(YELLOW + "Waiting up to 180 seconds for browser callback on " + prefs.getRedirectUri() + "..." + RESET);
                boolean success = receiver.waitForCallback(180);
                if (success) {
                    System.out.println(GREEN + BOLD + "Authorization successful! Tokens saved into config/userAuthorization.yaml" + RESET);
                } else {
                    System.out.println(RED + "Authorization timed out or failed." + RESET);
                }
            } catch (Exception e) {
                System.out.println(RED + "Could not start local callback server: " + e.getMessage() + RESET);
            } finally {
                if (receiver != null) {
                    receiver.stop();
                }
            }
        } else {
            System.out.print("\nEnter the 'code' parameter from the redirect URL: ");
            String code = scanner.nextLine().trim();
            if (!code.isEmpty()) {
                boolean success = oAuthService.exchangeCodeForTokens(code);
                if (success) {
                    System.out.println(GREEN + BOLD + "Token exchange successful! Tokens saved." + RESET);
                } else {
                    System.out.println(RED + "Token exchange failed. Check console error logs." + RESET);
                }
            }
        }
    }

    private void refreshAccessTokenManually() {
        System.out.println(CYAN + "\nRefreshing access token..." + RESET);
        boolean ok = oAuthService.refreshAccessToken();
        if (ok) {
            UserAuthorization auth = configManager.getUserAuthorization();
            System.out.println(GREEN + BOLD + "Token refresh successful!" + RESET);
            System.out.println(" New expiry: " + auth.getRemainingSeconds() + " seconds remaining");
            System.out.println(" userAuthorization.yaml updated.");
        } else {
            System.out.println(RED + "Token refresh failed. Ensure refresh token is present in userAuthorization.yaml." + RESET);
        }
    }

    private void listSupportedDataTypes() {
        List<DataTypeDefinition> list = dataTypeRegistry.getAllDataTypes();
        System.out.println(CYAN + BOLD + "\n--- Supported Google Health API Data Types (" + list.size() + ") ---" + RESET);
        System.out.printf("%-24s %-9s %-14s %-12s %-20s %s\n", "NAME", "VERSION", "UNIT", "WEBHOOKS", "RANGE", "ENDPOINTS");
        System.out.println("-".repeat(105));
        for (DataTypeDefinition def : list) {
            String rangeStr = "[" + (def.getMinValue() != null ? def.getMinValue() : "0") + ", " +
                    (def.getMaxValue() != null ? def.getMaxValue() : "inf") + "]";
            System.out.printf("%-24s %-9s %-14s %-12s %-20s %s\n",
                    BOLD + def.getName() + RESET,
                    def.getEndpointVersion(),
                    def.getUnit() != null ? def.getUnit() : "-",
                    def.isWebhooksSupported() ? GREEN + "YES" + RESET : "NO",
                    rangeStr,
                    String.join(", ", def.getSupportedEndpointNames()));
        }
        System.out.println("------------------------------------------------------------------------");
        System.out.print("Options: [A] Add New Data Type Setting  |  [ENTER] Return: ");
        String action = scanner.nextLine().trim();
        if ("a".equalsIgnoreCase(action)) {
            promptAddNewDataType();
        }
    }

    private void promptAddNewDataType() {
        System.out.println(CYAN + BOLD + "\n--- Add Additional Data Type Setting ---" + RESET);
        System.out.print("Enter Data Type Identifier (name, e.g. blood_pressure): ");
        String name = scanner.nextLine().trim().toLowerCase();
        if (name.isEmpty()) {
            System.out.println(RED + "Name cannot be empty." + RESET);
            return;
        }

        if (dataTypeRegistry.hasDataType(name)) {
            System.out.println(RED + "Data type '" + name + "' already exists in registry." + RESET);
            return;
        }

        System.out.print("Enter Display Name [default: " + name + "]: ");
        String displayName = scanner.nextLine().trim();
        if (displayName.isEmpty()) displayName = name;

        System.out.print("Enter Endpoint Version [default: v4]: ");
        String version = scanner.nextLine().trim();
        if (version.isEmpty()) version = "v4";

        System.out.print("Enter Measurement Unit (e.g. mmHg, count, bpm): ");
        String unit = scanner.nextLine().trim();

        System.out.print("Enter Minimum Valid Value [default: 0]: ");
        String minStr = scanner.nextLine().trim();
        Double minVal = minStr.isEmpty() ? 0.0 : Double.parseDouble(minStr);

        System.out.print("Enter Maximum Valid Value [default: 1000]: ");
        String maxStr = scanner.nextLine().trim();
        Double maxVal = maxStr.isEmpty() ? 1000.0 : Double.parseDouble(maxStr);

        System.out.print("Enter Read Scope [default: https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly]: ");
        String scope = scanner.nextLine().trim();
        if (scope.isEmpty()) {
            scope = "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly";
        }

        System.out.print("Supports Webhooks? (y/N) [default: N]: ");
        String wh = scanner.nextLine().trim();
        boolean webhooks = "y".equalsIgnoreCase(wh) || "yes".equalsIgnoreCase(wh);

        DataTypeDefinition newDef = new DataTypeDefinition(name, displayName, version, scope,
                List.of("list", "get", "create", "batchDelete"), name + ".sample_time", webhooks, minVal, maxVal, unit);

        boolean saved = dataTypeRegistry.addDataType(newDef);
        if (saved) {
            System.out.println(GREEN + BOLD + "Data type '" + name + "' successfully added to registry and config/datatypes.yaml!" + RESET);
        } else {
            System.out.println(RED + "Failed to add data type." + RESET);
        }
    }

    private void runSingleDataTypeTest() {
        List<String> names = dataTypeRegistry.getDataTypeNames();
        System.out.println(CYAN + BOLD + "\n--- Run Single Data Type Test ---" + RESET);
        System.out.println("Available data types:");
        for (int i = 0; i < names.size(); i++) {
            System.out.printf(" %2d. %s\n", (i + 1), names.get(i));
        }
        System.out.print("Select data type number [1-" + names.size() + "]: ");
        String sel = scanner.nextLine().trim();
        try {
            int idx = Integer.parseInt(sel) - 1;
            if (idx < 0 || idx >= names.size()) {
                System.out.println(RED + "Invalid selection." + RESET);
                return;
            }
            String chosenType = names.get(idx);
            DataTypeDefinition def = dataTypeRegistry.getDataType(chosenType).orElseThrow();

            System.out.println("\nSelected: " + BOLD + def.getName() + RESET + " (Version: " + CYAN + def.getEndpointVersion() + RESET + ")");
            System.out.println("Supported endpoints: " + String.join(", ", def.getSupportedEndpointNames()));
            System.out.print("Enter operation (list/get/create/rollup/dailyrollup) [default: list]: ");
            String ep = scanner.nextLine().trim();
            if (ep.isEmpty()) ep = "list";

            System.out.println(CYAN + "\nQuerying " + def.getEndpointVersion() + " endpoint and sending HTTP request..." + RESET);
            TestResult result = engine.executeSingleTest(def.getName(), ep, null, null);
            printTestResult(result);

        } catch (NumberFormatException e) {
            System.out.println(RED + "Invalid number format." + RESET);
        }
    }

    private void runFullTestSuite() {
        System.out.println(CYAN + BOLD + "\n--- Running Comprehensive Test Suite Across All Data Types ---" + RESET);
        long start = System.currentTimeMillis();
        List<TestResult> results = engine.executeAllDataTypesTest("list");
        long duration = System.currentTimeMillis() - start;

        int passed = 0;
        int failed = 0;
        for (TestResult r : results) {
            printTestResult(r);
            if (r.isPassed()) passed++;
            else failed++;
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.printf(" TEST SUITE SUMMARY: Total: %d | Passed: %s%d%s | Failed: %s%d%s | Time: %dms\n",
                results.size(), GREEN, passed, RESET, (failed > 0 ? RED : GREEN), failed, RESET, duration);
        System.out.println("=".repeat(60));
    }

    private void runProfileAndDeviceTests() {
        System.out.println(CYAN + BOLD + "\n--- Testing User Profile & Paired Devices ---" + RESET);

        System.out.println("1. Testing GET /v4/users/{userId}/profile ...");
        ApiResponse profileResp = engine.getApiClient().getProfile(null);
        System.out.printf("   HTTP %d (%dms): %s\n", profileResp.getStatusCode(), profileResp.getLatencyMs(),
                profileResp.isSuccess() ? GREEN + "OK" + RESET : RED + "FAILED" + RESET);
        if (!profileResp.getBody().isEmpty()) {
            System.out.println("   Response: " + truncate(profileResp.getBody(), 150));
        }

        System.out.println("\n2. Testing GET /v4/users/{userId}/pairedDevices ...");
        ApiResponse devResp = engine.getApiClient().listPairedDevices(null);
        System.out.printf("   HTTP %d (%dms): %s\n", devResp.getStatusCode(), devResp.getLatencyMs(),
                devResp.isSuccess() ? GREEN + "OK" + RESET : RED + "FAILED" + RESET);
        if (!devResp.getBody().isEmpty()) {
            System.out.println("   Response: " + truncate(devResp.getBody(), 150));
        }
    }

    private void runScriptFilePrompt() {
        System.out.println(CYAN + BOLD + "\n--- Run Test Script File (Mode 3) ---" + RESET);
        File scriptsDir = new File("scripts");
        if (scriptsDir.exists() && scriptsDir.isDirectory()) {
            File[] files = scriptsDir.listFiles((dir, name) -> name.endsWith(".yaml") || name.endsWith(".json"));
            if (files != null && files.length > 0) {
                System.out.println("Found scripts in scripts/:");
                for (int i = 0; i < files.length; i++) {
                    System.out.printf(" %d. %s\n", (i + 1), files[i].getPath());
                }
            }
        }
        System.out.print("Enter script file path [e.g. scripts/sample_suite.yaml]: ");
        String path = scanner.nextLine().trim();
        if (path.isEmpty()) path = "scripts/sample_suite.yaml";

        ScriptRunner scriptRunner = new ScriptRunner(engine);
        int exitCode = scriptRunner.runScript(new File(path));
        System.out.println("\nScript runner completed with exit code: " + exitCode);
    }

    private void toggleMockMode() {
        Preferences prefs = configManager.getPreferences();
        boolean newMode = !prefs.isMockMode();
        prefs.setMockMode(newMode);
        configManager.savePreferences(prefs);
        System.out.println(GREEN + "Switched to: " + (newMode ? "MOCK/SIMULATION MODE" : "LIVE GOOGLE API MODE") + RESET);
    }

    private void printTestResult(TestResult r) {
        String badge = r.isPassed() ? (GREEN + "[PASS]" + RESET) : (RED + "[FAIL]" + RESET);
        System.out.printf(" %s %-32s HTTP %3d (%4dms) - %s\n",
                badge, r.getTestName(), r.getStatusCode(), r.getLatencyMs(), r.getMessage());
        if (r.getApiResponse() != null && !r.isPassed()) {
            System.out.println(YELLOW + "   Request URL: " + r.getApiResponse().getRequestUrl() + RESET);
            System.out.println(YELLOW + "   Response: " + truncate(r.getApiResponse().getBody(), 120) + RESET);
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() > max ? clean.substring(0, max) + "..." : clean;
    }
}
