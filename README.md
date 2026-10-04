# Google Health API Test Suite

A comprehensive, production-grade test suite and API inspection platform for the **Google Health API (v4)** built in **Java** and **JavaScript/HTML**. Built with **Apache Maven**, the test suite uses standard reusable HTTP requests across all supported health and biometric data types, features automatic OAuth 2.0 token expiration detection and refresh persistence, and provides a single declarative YAML catalog for all Google Health data types with range validation.

Official Google Health API Documentation: [https://developers.google.com/health](https://developers.google.com/health)

---

## 🌟 Key Features

- **3 Execution Modes Supported Out-of-the-Box**:
  1. **Interactive Command-Line Menu**: Full-featured terminal interface with ANSI colors and interactive menus.
  2. **Modern Web UX (JavaScript / HTML)**: Responsive dark-theme dashboard with live token expiration countdowns, visual API request builder, cURL generator, live response inspector, and one-click test runner.
  3. **Non-Interactive Script File Runner**: Headless runner executing YAML/JSON test script files with query filters, status assertions, and min/max value validation (exit code 0 on pass, 1 on failure for CI/CD).
- **Standardized Reusable HTTP Client**:
  - Consistent REST conventions: `GET/POST /v4/users/{userId}/dataTypes/{dataType}/dataPoints...`
  - Reusable execution methods across all data types (`list`, `get`, `create`, `rollUp`, `dailyRollUp`, `batchDelete`, `profile`, `pairedDevices`, `subscriptions`).
  - Auto-captures latency, response headers, status codes, formatted JSON payloads, and reproducible `curl` commands.
- **Single File Data Types Catalog (`config/datatypes.yaml`)**:
  - Declares all supported Google Health API data types (`steps`, `heart_rate`, `distance`, `weight`, `height`, `sleep`, `blood_glucose`, `oxygen_saturation`, etc.).
  - Specifies required OAuth scopes, supported endpoints, AIP-160 filter parameter names, webhook support flags, and numerical minimum/maximum value limits.
  - Simple structure: adding new data types is effortless and consistent.
- **Automated Token Expiration & Refresh Persistence (`config/userAuthorization.yaml`)**:
  - Stores `healthUserID`, `accessToken`, `refreshToken`, and expiration epoch timestamps.
  - Automatically detects token expiration (prior to requests or upon receiving HTTP 401 Unauthorized), calls Google's OAuth token endpoint to rotate credentials, and immediately updates and saves `userAuthorization.yaml` to disk.
- **Application Preferences (`config/preferences.yaml`)**:
  - Stores OAuth 2.0 `clientId`, `clientSecret`, `redirectUri`, `authUri`, `tokenUri`, `apiBaseUrl`, and list of scopes to request.
- **Dual Live & Mock/Simulation Modes**:
  - Full simulation mode allows testing all 3 modes immediately without active Google Cloud credentials.
  - Seamlessly switch between Mock Mode and Live Google Health API mode with a single toggle or `--mock` flag.

---

## 📁 Project Structure

```
google-health-api-test-suite-java/
├── pom.xml                               # Maven project configuration (Google Client libraries, Jackson, JUnit 5)
├── README.md                             # Comprehensive documentation and instructions
├── run.sh                                # Easy multi-mode runner script
├── config/
│   ├── datatypes.yaml                    # Single declarative file defining all Google Health data types
│   ├── preferences.yaml                  # Stores Client ID, Secret, and OAuth scopes
│   ├── preferences.example.yaml          # Template preferences file
│   ├── userAuthorization.yaml            # Stores healthUserID, accessToken, refreshToken, expiry
│   └── userAuthorization.example.yaml    # Template authorization file
├── scripts/
│   ├── sample_suite.yaml                 # Sample multi-step test script with assertions
│   ├── smoke_test.yaml                   # Fast sanity check script
│   └── full_regression.yaml              # Comprehensive regression suite covering 9 data types
├── src/
│   ├── main/
│   │   ├── java/com/google/health/testsuite/
│   │   │   ├── Main.java                 # Entry point dispatching to Menu, Web, or Script modes
│   │   │   ├── auth/
│   │   │   │   ├── OAuthService.java     # Auth URL builder, code exchange & auto-refresh
│   │   │   │   └── LocalOAuthReceiver.java # Temporary HTTP server for local OAuth redirect
│   │   │   ├── client/
│   │   │   │   ├── HealthApiClient.java  # Reusable HTTP client for all data types & auto-retry
│   │   │   │   ├── MockHealthBackend.java# Offline simulator for Google Health API v4
│   │   │   │   └── ValidationEngine.java # Range validator against datatypes.yaml constraints
│   │   │   ├── config/
│   │   │   │   ├── ConfigManager.java    # Reads & atomically persists preferences and auth
│   │   │   │   ├── DataTypeRegistry.java # Loads and queries datatypes.yaml
│   │   │   │   ├── Preferences.java      # Preferences model
│   │   │   │   └── UserAuthorization.java# Authorization model with expiry check
│   │   │   ├── model/                    # ApiResponse, DataTypeDefinition, TestResult, TestStep
│   │   │   ├── runner/
│   │   │   │   ├── CliMenuRunner.java    # Mode 1: Interactive terminal menu
│   │   │   │   ├── ScriptRunner.java     # Mode 3: Test script runner
│   │   │   │   └── SuiteExecutionEngine.java # Core test engine shared across all modes
│   │   │   └── server/
│   │   │       ├── WebServer.java        # Mode 2: Embedded HTTP server
│   │   │       └── RestApiHandler.java   # REST API endpoints for Web UX
│   │   └── resources/web/
│   │       ├── index.html                # Modern responsive dashboard
│   │       ├── style.css                 # Dark theme with glassmorphism styling
│   │       └── app.js                    # Reactive frontend logic & live timers
│   └── test/java/com/google/health/testsuite/
│       ├── ConfigManagerTest.java        # Tests atomic file persistence & tokens
│       ├── DataTypeRegistryTest.java     # Tests datatypes.yaml loading & constraints
│       ├── HealthApiClientTest.java      # Tests standard HTTP client & mock backend
│       ├── ScriptRunnerTest.java         # Tests automated script execution
│       └── ValidationEngineTest.java     # Tests min/max range checks
```

---

## 🚀 How to Run the Application (3 Modes)

The test suite can be run using the included `./run.sh` launcher or standard Java / Maven commands:

### Mode 1: Using a Command Line Menu

An interactive, styled terminal menu that lets you view tokens, refresh credentials, explore data types, execute single API requests, and run the complete test suite.

```bash
# Using the launcher:
./run.sh menu

# Or using java directly:
java -jar target/health-api-testsuite.jar --menu

# To launch in Mock Mode (offline simulation without credentials):
./run.sh menu --mock
```

**Menu Navigation:**
```
[ MAIN MENU ]
 Mode: LIVE GOOGLE API | HealthUserID: 8677373576871223311 | Token: VALID (3450s remaining)
------------------------------------------------------------------------
 1. Preferences & Auth Menu (Dashboard, Auth, getIdentity, getDevices)
 2. View Authorization & Token Details
 3. Authorize with Google (OAuth 2.0 Web Callback / Manual Code)
 4. Refresh Access Token Now (Automatic Rotation & Save)
 5. List Supported Data Types (From datatypes.yaml)
 6. Run Single Data Type API Test (List, Get, Create, Rollup)
 7. Run Comprehensive Test Suite Across All Data Types
 8. Test Identity, Profile & Paired Devices Endpoints
 9. Run a Test Script File (Mode 3 Script Runner)
 10. Toggle Mock/Live Mode
 0. Exit
------------------------------------------------------------------------
```

---

### Mode 2: Using Web UX (JavaScript / HTML Dashboard)

A lightweight embedded web server hosts the interactive dashboard at `http://localhost:8080` (or custom port).

```bash
# Using the launcher (default port 8080):
./run.sh web 8080

# Or using java directly:
java -jar target/health-api-testsuite.jar --web 8080

# To run in Mock Mode:
./run.sh web 8080 --mock
```

Open your browser to: **`http://localhost:8080`**

**Web UX Capabilities (Menu Order):**
1. **Interactive API Explorer**:
   - Dropdown populated dynamically from `config/datatypes.yaml`.
   - Displays endpoint version (e.g. `v4`), required scopes, AIP-160 filter parameter syntax, webhook support status, and valid min/max ranges.
   - Select operations: `list`, `get`, `create`, `rollUp`, `dailyRollUp`, `batchDelete`.
   - Generates live, copyable `curl` commands.
   - Color-coded HTTP status pills (`200 OK`, `401`, `404`), latency badges, min/max range check badge (`PASS` / `FAIL`), and syntax-highlighted JSON response viewer.
2. **Full Test Suite Runner**: One-click test runner across all data types with animated progress bar, pass/fail counters, average latency, and an interactive results table.
3. **Script Runner**: Select and run script files from the UI and observe live console output.
4. **Data Types Registry**:
   - Complete searchable and filterable catalog of all Google Health API data types.
   - **"+ Add Data Type Setting"**: In-app setting to register additional data types with endpoint version, valid ranges, units, scopes, and supported endpoints. Changes are saved directly to `config/datatypes.yaml` and hot-reloaded into the running test suite immediately.
5. **Preferences & Auth**:
   - **Live Token & Auth Status**: Live countdown timer for the access token, authorization status pills, and one-click **"Force Token Refresh"** and **"Authorize with Google"** buttons.
   - **Identity & Devices Endpoints**:
     - **`getIdentity`** (`GET /v4/users/{userId}/identity`): Queries Google Health identity mapping. If `healthUserId` is missing in `config/preferences.yaml`, it automatically stores the discovered user ID into `preferences.yaml`.
     - **`getDevices`** (`GET /v4/users/{userId}/pairedDevices`): Retrieves connected smartwatches, fitness trackers, and devices.
     - **Pretty JSON Viewer**: Displays API responses with latency and HTTP status in formatted, indented JSON.
   - **Configuration Form**: Edit OAuth Client ID, Secret, Health User ID (`healthUserId`), Redirect URI, API Base URL, and Mock Mode with instant persistence.

---

### Mode 3: Through a Script File

Non-interactive automated runner for continuous integration, regression testing, or batch validation.

```bash
# Using the launcher:
./run.sh script scripts/sample_suite.yaml

# Run regression test in Mock Mode:
./run.sh script scripts/full_regression.yaml --mock

# Or using java directly:
java -jar target/health-api-testsuite.jar --script scripts/sample_suite.yaml
```

**Script Execution Output Example:**
```
================================================================================
 RUNNING TEST SCRIPT: Google Health API End-to-End Sample Test Suite
 Description: Verifies user identity, device pairing, and query endpoints for activity and biometric metrics
 Script File: .../scripts/sample_suite.yaml
 Steps:       8
================================================================================
[ 1/ 8] Running: Verify Stored Authorization Tokens  ... PASS (HTTP 200, 0ms)
[ 2/ 8] Running: Fetch User Profile Identity         ... PASS (HTTP 200, 40ms)
[ 3/ 8] Running: List Paired Devices                 ... PASS (HTTP 200, 35ms)
[ 4/ 8] Running: Query Steps DataPoints              ... PASS (HTTP 200, 37ms)
[ 5/ 8] Running: Query Heart Rate Samples            ... PASS (HTTP 200, 35ms)
[ 6/ 8] Running: Query Distance DataPoints           ... PASS (HTTP 200, 35ms)
[ 7/ 8] Running: Query Weight Measurements           ... PASS (HTTP 200, 35ms)
[ 8/ 8] Running: RollUp Steps Aggregation            ... PASS (HTTP 200, 35ms)
================================================================================
 EXECUTION SUMMARY: Total: 8 | Passed: 8 | Failed: 0 | Time: 18ms
================================================================================
```

*Exit Codes:*
- `0`: All test steps passed assertions.
- `1`: One or more test steps failed (ideal for CI/CD exit status).

---

## 📝 Script File Syntax (`scripts/*.yaml`)

Script files allow defining custom sequences of API calls and assertions in YAML or JSON:

```yaml
name: "Google Health Custom Daily Test"
description: "Checks sync of steps and heart rate"
stopOnError: false # When true, stops execution on first failing step

steps:
  - name: "Check Credentials"
    action: "check_auth"
    assertStatus: [200]

  - name: "Query Steps for Today"
    action: "list"               # Supported actions: list, get, create, rollup, dailyrollup, batchdelete, profile, devices, check_auth
    dataType: "steps"            # Data type name from datatypes.yaml
    queryParams:
      pageSize: "50"
    assertStatus: [200]          # Expected HTTP status code(s)
    validateRange: true          # Validates response measurements against min/max in datatypes.yaml
    assertContains:              # Optional expected substrings
      - "dataPoints"

  - name: "Query Heart Rate"
    action: "list"
    dataType: "heart_rate"
    queryParams:
      pageSize: "25"
    assertStatus: [200]
    validateRange: true
```

---

## 📊 Data Types Catalog (`config/datatypes.yaml`)

All data types supported by the Google Health API are declared and maintained in a single file: `config/datatypes.yaml`. Each data type explicitly specifies the API endpoint version it supports (e.g., `v4`), and the test suite's HTTP client dynamically queries this version when calling endpoints.

### Structure of a Data Type Entry:

```yaml
- name: "steps"                                      # Datatype name used in API path
  displayName: "Step Count"                          # Human-readable title
  endpointVersion: "v4"                              # Supported API endpoint version (e.g. "v4")
  scopeRequired: "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly" # Read scope
  writeScopeRequired: "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.writeonly" # Write scope
  endpointsSupported:                                # Map of all REST operations (true if supported, false if not)
    list: true
    get: true
    create: true
    batchDelete: true
    rollUp: true
    dailyRollUp: true
    exportExerciseTcx: false
    reconcile: false
    patch: false
  filterParameterName: "steps.interval.start_time"   # AIP-160 filter field
  webhooksSupported: true                            # Webhook subscription capability
  minValue: 0                                        # Documented minimum valid value
  maxValue: 1000000                                  # Documented maximum valid value
  unit: "count"                                      # Measurement unit
  sampleValueField: "steps.count"                    # JSON path for value extraction
```

### Supported Data Types & Endpoint Versions:

| Data Type Name | Version | Unit | Webhooks | Valid Range | Endpoints Supported |
| :--- | :---: | :--- | :---: | :--- | :--- |
| `steps` | `v4` | count | ✅ Yes | [0, 1,000,000] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `distance` | `v4` | millimeters | ✅ Yes | [0, 1,000,000,000] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `floors` | `v4` | count | ✅ Yes | [0, 1,000,000] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `altitude` | `v4` | meters | ✅ Yes | [-500, 9,000] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `active_energy_burned` | `v4` | kcal | ❌ No | [0, 1,000,000] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `basal_energy_burned` | `v4` | kcal | ❌ No | [0, 1,000,000] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `active_zone_minutes` | `v4` | minutes | ❌ No | [0, 1,440] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `heart_rate` | `v4` | bpm | ❌ No | [1, 300] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `daily_resting_heart_rate`| `v4` | bpm | ❌ No | [20, 250] | list, get, dailyRollUp |
| `heart_rate_variability`| `v4` | ms | ❌ No | [0, 500] | list, get, create, batchDelete |
| `weight` | `v4` | grams | ✅ Yes | [0, 1,000,000] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `height` | `v4` | millimeters | ❌ No | [0, 3,000] | list, get, create, batchDelete |
| `body_fat` | `v4` | percentage | ❌ No | [0, 100] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `oxygen_saturation` | `v4` | percentage | ❌ No | [0, 100] | list, get, create, batchDelete |
| `blood_glucose` | `v4` | mg/dL | ❌ No | [0, 900] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `sleep` | `v4` | seconds | ✅ Yes | [0, 86,400] | list, get, create, batchDelete, reconcile |
| `mindfulness` | `v4` | seconds | ❌ No | [0, 86,400] | list, get, create, batchDelete |
| `exercise` | `v4` | seconds | ❌ No | [0, 86,400] | list, get, exportExerciseTcx |
| `electrocardiogram` | `v4` | samples | ❌ No | [0, 500] | list, get |
| `blood_pressure` | `v4` | mmHg | ❌ No | [0, 300] | list, get, create, batchDelete |

### Adding Additional Data Types:

Additional health metrics and data types can be added through three mechanisms without modifying application source code:

1. **Via Web UX Dashboard**:
   - Open **Data Types Registry** tab.
   - Click the **"+ Add Data Type Setting"** button to toggle the registration panel.
   - Specify Name (ID), Display Name, Endpoint Version (default: `v4`), Unit, Min/Max Bounds, Read/Write Scopes, Filter Parameter, and Supported Endpoints.
   - Click **Save & Register Data Type**. The application automatically appends the entry to `config/datatypes.yaml`, reloads the registry in memory, and immediately updates the catalog and API Explorer dropdown.

2. **Via Command Line Menu (Mode 1)**:
   - Select option `5. List Supported Data Types`.
   - Choose `[A] Add New Data Type Setting`.
   - Follow the interactive prompts to define the name, version, unit, bounds, scopes, and endpoints. The new definition is immediately persisted to `config/datatypes.yaml` and loaded into the active session.

3. **Directly in YAML (`config/datatypes.yaml`)**:
   - Add a new YAML element adhering to the structure shown above. The test suite automatically validates ranges, binds endpoints, and attaches the appropriate API version prefix.

## 🔐 Authorization & Token Management (`userAuthorization.yaml`)

The application automatically manages OAuth tokens in `config/userAuthorization.yaml`:

```yaml
healthUserID: "me"
accessToken: "ya29.a0AfH6SMA..."
refreshToken: "1//04..."
tokenType: "Bearer"
expiresAtEpochMs: 1735689600000
updatedAt: "2026-10-04T02:00:00Z"
scope: "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly ..."
```

---

## 🔐 Authorizing the Connection

The test suite provides several seamless ways to execute OAuth 2.0 authorization with Google and store your tokens:

### Option A: Direct Authorization Flow (CLI / Terminal)
Run the dedicated authorization command directly:
```bash
# Using the launcher script:
./run.sh auth

# Or using java directly:
java -jar target/health-api-testsuite.jar --auth
# (or with shorthand -a)
```
This command:
1. Starts a temporary local HTTP callback server on your configured `redirect_uri` (e.g. `http://localhost:8888/callback`).
2. Automatically launches your default web browser to the Google OAuth consent screen.
3. Awaits the callback redirect, securely captures the authorization code, exchanges it with Google for access and refresh tokens, and saves them to `config/userAuthorization.yaml`.

### Option B: Interactive CLI Menu
1. Launch `./run.sh menu` or `mvn exec:java`.
2. Select **Option 3** (`Authorize with Google`).
3. The menu will start the local callback receiver, open your browser, and save the resulting tokens automatically.

### Option C: Modern Web UX Dashboard
1. Launch `./run.sh web 8080` and open `http://localhost:8080`.
2. On the **Dashboard & Auth** tab, click **"Authorize with Google"**.
3. The server starts the local receiver on port 8888 in the background and opens the Google OAuth consent page in your browser.
4. Once you approve access, the web dashboard automatically detects the new tokens, updates the live expiration countdown, and confirms successful authorization.

---

### Automatic Token Refresh Workflow:
1. **Pre-flight Check**: Before dispatching any HTTP request, `HealthApiClient` checks if the access token has expired (or has `< 60s` remaining).
2. **401 Interception**: If an API call receives an HTTP `401 Unauthorized` response from Google:
   - The client invokes `OAuthService.refreshAccessToken()`.
   - Google's token service (`https://oauth2.googleapis.com/token`) issues a new access token (and optional rotated refresh token).
   - The client updates `userAuthorization.yaml` using thread-safe atomic file writing.
   - The failed HTTP request is automatically retried with the new token.

---

## ⚙️ Application Preferences (`config/preferences.yaml`)

Stores your Google Cloud OAuth 2.0 credentials and requested scopes:

```yaml
clientId: "YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com"
clientSecret: "YOUR_GOOGLE_CLIENT_SECRET"
authUri: "https://accounts.google.com/o/oauth2/v2/auth"
tokenUri: "https://oauth2.googleapis.com/token"
redirect_uri: "http://localhost:8888/callback"
apiBaseUrl: "https://health.googleapis.com"
healthUserId: "8677373576871223311" # Populated automatically by getIdentity
defaultUserId: "me"
mockMode: false

scopes:
  - "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly"
  - "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.writeonly"
  - "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly"
  - "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.writeonly"
  - "https://www.googleapis.com/auth/googlehealth.sleep.readonly"
  - "https://www.googleapis.com/auth/googlehealth.sleep.writeonly"
  - "https://www.googleapis.com/auth/googlehealth.mindfulness.readonly"
  - "https://www.googleapis.com/auth/googlehealth.logged_symptoms.readonly"
  - "https://www.googleapis.com/auth/googlehealth.reproductive_health.readonly"
  - "https://www.googleapis.com/auth/googlehealth.ecg.readonly"
  - "https://www.googleapis.com/auth/googlehealth.location.readonly"
  - "https://www.googleapis.com/auth/googlehealth.profile.readonly"
  - "https://www.googleapis.com/auth/googlehealth.settings.readonly"
```

---

## 🛠️ Building & Testing

### Build with Maven:
```bash
# Download dependencies, compile, and run all unit/integration tests:
mvn clean test

# Build executable standalone shaded JAR (target/health-api-testsuite.jar):
mvn clean package -DskipTests
```

### Running the Test Suite via Maven:
```bash
# Mode 1: Interactive Menu
mvn exec:java

# Mode 2: Web UX
mvn exec:java -Dexec.args="--web 8080"

# Mode 3: Script Runner
mvn exec:java -Dexec.args="--script scripts/sample_suite.yaml"
```
