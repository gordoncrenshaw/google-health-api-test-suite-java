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
  - Declares all supported Google Health API data types (`steps`, `heart-rate`, `distance`, `weight`, `height`, `sleep`, `blood-glucose`, `oxygen-saturation`, etc.).
  - Specifies required OAuth scopes, supported endpoints, AIP-160 filter parameter names, webhook support flags, and numerical minimum/maximum value limits.
  - Simple structure: adding new data types is effortless and consistent.
- **Official Google OAuth2 Credential Management (`com.google.api.client.auth.oauth2.Credential`)**:
  - Securely stores and loads credentials via Google's `FileDataStoreFactory` in `tokens/` (which is git-ignored to prevent token leaks).
  - Automatically manages token expiration (prior to requests or upon receiving HTTP 401 Unauthorized), executing `Credential.refreshToken()` to rotate access tokens and update the credential data store.
- **OAuth 2.0 Client Credentials (`config/client_secret.json`)**:
  - Automatically reads `client_id` and `client_secret` from standard Google Cloud OAuth client secret JSON.
- **Application Preferences (`config/preferences.yaml`)**:
  - Stores `redirectUri`, `authUri`, `tokenUri`, `apiBaseUrl`, `healthUserId`, `endpointUserId`, `mockMode`, and list of scopes to request.
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
│   ├── client_secret.json                # Standard Google OAuth 2.0 client secrets JSON
│   ├── preferences.yaml                  # Application preferences, endpoint settings, and OAuth scopes
│   └── preferences.example.yaml          # Template preferences file
├── tokens/                               # Git-ignored directory storing com.google.api.client.auth.oauth2.Credential
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
 1. Preferences & Auth Menu (Dashboard, Auth, Identity, Profile & Settings)
 2. View Authorization & Token Details
 3. Authorize with Google (OAuth 2.0 Web Callback / Manual Code)
 4. Refresh Access Token Now (Automatic Rotation & Save)
 5. List Supported Data Types (From datatypes.yaml)
 6. Run Single Data Type API Test (List, Get, Create, Rollup)
 7. Run Comprehensive Test Suite Across All Data Types
 8. Test Identity, Profile, Settings & Paired Devices Endpoints
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
5. **Preferences & OAuth**:
   - **OAuth Scope Selector & Dynamic File Storage**:
     - All available scopes are defined in [`config/preferences.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/config/preferences.yaml). Adding new scopes to `preferences.yaml` dynamically presents them in the UI without restarting.
     - Interactive multi-select grid with individual selection, **"Select All"**, and **"Deselect All"** buttons.
     - Defaults to **all scopes deselected**.
     - Live preview of the generated OAuth `scope` query parameter string.
     - **"Authorize with Google"** / **"Authorize with Selected Scopes"** initiates the Google OAuth consent flow using exactly the selected scopes.
   - **Live Token & Auth Status**: Live countdown timer for the access token, authorization status pills, and one-click **"Force Token Refresh"** button.
   - **Identity, Profile & Settings Endpoints**:
     - **`getIdentity`** (`GET /v4/users/{userId}/identity`): Queries Google Health identity mapping. If `healthUserId` is missing in `config/preferences.yaml`, it automatically stores the discovered user ID into `preferences.yaml`.
     - **`getDevices`** (`GET /v4/users/{userId}/pairedDevices`): Retrieves connected smartwatches, fitness trackers, and devices.
     - **`getProfile`** (`GET /v4/users/{userId}/profile`): Retrieves user profile details.
     - **`updateProfile`** (`PATCH /v4/users/{userId}/profile`): Updates user profile attributes (such as `displayName` or `locale`) with custom JSON payloads.
     - **`getIrnProfile`** (`GET /v4/users/{userId}/irnProfile`): Retrieves Irregular Rhythm Notifications (IRN) AFib detection status.
     - **`getSettings`** (`GET /v4/users/{userId}/settings`): Retrieves user measurement units, locale, and timezone settings.
     - **`updateSettings`** (`PATCH /v4/users/{userId}/settings`): Updates user measurement units, locale, and timezone settings with custom JSON payloads.
     - **Pretty JSON Viewer & In-App Payload Editor**: Displays API responses with latency and HTTP status in formatted, indented JSON with interactive JSON payload inputs.
   - **Configuration Form**: Edit OAuth Client ID, Secret, Health User ID (`healthUserId`), Redirect URI, API Base URL, Mock Mode, and **"Enable All Endpoints for Each Datatype"** (overrides datatype endpoint definitions to enable all endpoints across the test suite) with instant persistence.

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

Script files allow defining custom sequences of automated test steps and assertions in YAML or JSON for CI/CD, regression testing, or headless batch runs.

### Top-Level Suite Attributes

| Attribute Name | Type | Required | Default | Description & Supported Values |
| :--- | :---: | :---: | :---: | :--- |
| `name` | String | No | `"Unnamed Test Script"` | Display name/title of the test script suite. |
| `description` | String | No | `""` | Optional description explaining the scope of the test script. |
| `stopOnError` | Boolean | No | `false` | When `true`, halts execution immediately upon the first step failure. When `false`, continues running all remaining steps. |
| `steps` | List | **Yes** | `[]` | Ordered list of test step objects to execute. |

---

### Supported Attributes for Adding a Test Step (`steps[]`)

Each test step entry in `steps` supports the following attribute names and values:

| Attribute Name | Type | Required | Default | Supported Values & Description |
| :--- | :---: | :---: | :---: | :--- |
| `name` | String | No | Auto-generated | Human-readable label for the step. If omitted, defaults to `<action> (<dataType>)` (e.g., `"list (steps)"`). |
| `action` | String | No | `"list"` | The API operation or check to execute. See [Supported Actions](#supported-actions) below for all available options. |
| `dataType` | String | Conditional | `null` | Required for data point actions (`list`, `get`, `create`, `rollup`, `dailyrollup`, `reconcile`, `patch`, etc.). Must match a registered data type name from `config/datatypes.yaml` (e.g., `steps`, `heart-rate`, `distance`, `weight`). |
| `dataPointId` | String | Conditional | `"default-dp"` / `"sample-dp-1"` | Identifier of the specific data point. Used by single-point operations (`get`, `patch`, `exportexercisetcx`). |
| `userId` | String | No | `null` (active pref) | User identifier in URL syntax (`/v4/users/{userId}/...`). Supported values: `"me"`, a specific numeric Health User ID (e.g., `"7226778021918831098"`), or `"auto"` (falls back to active `endpointUserId` from `config/preferences.yaml`). |
| `queryParams` | Map / Object | No | `{}` | Key-value pairs appended as URL query parameters (e.g., `pageSize: "25"`, `pageToken: "..."`, `filter: "..."`). |
| `body` | String | Conditional | `null` | Raw JSON string payload for requests that send data (`create`, `update_profile`, `update_settings`, `rollup`, `dailyrollup`, `patch`, `batchdelete`). |
| `assertStatus` | Integer or List of Integers | No | `[200]` | Expected HTTP status codes for the step to pass. Accepts a single integer (e.g., `200`) or a YAML list of integers (e.g., `[200]`, `[200, 204]`, `[404]`). |
| `validateRange` | Boolean | No | `true` | Validates returned numerical measurements in the response payload against the `minValue` and `maxValue` defined in `config/datatypes.yaml`. Supported values: `true` or `false`. |
| `assertContains` | List of Strings | No | `[]` | List of substrings that must be present in the raw HTTP response body for the test to pass (e.g., `["dataPoints"]`, `["temperatureUnit"]`). |

---

### Supported Actions (`action`)

The `action` attribute supports the following operations and alias names:

#### 1. Data Type & DataPoint Operations (Require `dataType`)
| Action Name | Aliases | Method / Endpoint | Description |
| :--- | :--- | :--- | :--- |
| `list` | `list_datapoints` | `GET /v4/users/{userId}/dataTypes/{dataType}/dataPoints` | Queries data points for the given data type with optional `queryParams`. |
| `get` | `get_datapoint` | `GET /v4/users/{userId}/dataTypes/{dataType}/dataPoints/{dataPointId}` | Retrieves a single data point by `dataPointId`. |
| `create` | `create_datapoint` | `POST /v4/users/{userId}/dataTypes/{dataType}/dataPoints` | Inserts a new data point using JSON in `body`. |
| `rollup` | - | `POST /v4/users/{userId}/dataTypes/{dataType}/dataPoints:rollUp` | Computes physical aggregate rollups over intervals. Optional JSON in `body`. |
| `dailyrollup` | `daily_rollup` | `POST /v4/users/{userId}/dataTypes/{dataType}/dataPoints:dailyRollUp` | Computes daily aggregate rollups. Optional JSON in `body`. |
| `reconcile` | - | `GET /v4/users/{userId}/dataTypes/{dataType}/dataPoints:reconcile` | Reconciles deduplicated data points across sources. Accepts optional `queryParams` (`pageSize`, `pageToken`, `filter`, `dataSourceFamily`). |
| `patch` | `patch_datapoint` | `PATCH /v4/users/{userId}/dataTypes/{dataType}/dataPoints/{dataPointId}` | Updates an existing data point by ID with JSON `body`. |
| `exportexercisetcx` | `export_exercise_tcx` | `GET /v4/users/{userId}/dataTypes/{dataType}/dataPoints/{dataPointId}:exportTcx` | Exports GPS and heart rate TCX track XML for an exercise data point. |
| `batchdelete` | `batch_delete` | `POST /v4/users/{userId}/dataTypes/{dataType}/dataPoints:batchDelete` | Deletes multiple data points by name. Sends JSON `body` or `queryParams.names`. |

#### 2. User Identity, Profile & Settings Operations
| Action Name | Aliases | Method / Endpoint | Description |
| :--- | :--- | :--- | :--- |
| `identity` | `get_identity` | `GET /v4/users/{userId}/identity` | Retrieves user identity mapping (e.g., `healthUserId`). |
| `profile` | `get_profile` | `GET /v4/users/{userId}/profile` | Retrieves user profile metadata (`displayName`, `locale`, `userConfiguredWalkingStrideLengthMm`, etc.). |
| `update_profile` | `patch_profile` | `PATCH /v4/users/{userId}/profile` | Updates user profile attributes with JSON `body`. |
| `settings` | `get_settings` | `GET /v4/users/{userId}/settings` | Retrieves user settings (`temperatureUnit`, `timeZone`, etc.). |
| `update_settings` | `patch_settings` | `PATCH /v4/users/{userId}/settings` | Updates user settings with JSON `body` (e.g., `'{"temperatureUnit": "TEMPERATURE_UNIT_FAHRENHEIT"}'`). |
| `irn_profile` | `get_irn_profile`, `irnprofile` | `GET /v4/users/{userId}/irnProfile` | Retrieves Irregular Rhythm Notifications (IRN) AFib feature enablement status. |
| `devices` | `list_devices` | `GET /v4/users/{userId}/pairedDevices` | Lists connected smartwatches, fitness trackers, and paired hardware. |

#### 3. Authentication & Pre-Check Operations
| Action Name | Aliases | Description |
| :--- | :--- | :--- |
| `check_auth` | - | Verifies that a valid OAuth access token is stored in the credential store or simulation/mock mode is active. |

---

### Supported Data Types (`dataType`)

When executing data point actions (`list`, `get`, `create`, `rollup`, `dailyrollup`, `batchdelete`), `dataType` accepts any identifier declared in `config/datatypes.yaml`:

- **Activity & Energy**: `steps`, `distance`, `floors`, `altitude`, `active-energy-burned`, `basal-energy-burned`, `active-zone-minutes`, `exercise`
- **Biometrics & Vitals**: `heart-rate`, `daily-resting-heart-rate`, `heart-rate-variability`, `electrocardiogram`
- **Body Measurements**: `weight`, `height`, `body-fat`
- **Health Metrics**: `blood-glucose`, `oxygen-saturation`, `skin-temperature-sensors`
- **Sleep**: `sleep`
- Any custom data type added to `config/datatypes.yaml`.

---

### Complete Example Script (`scripts/example_suite.yaml`)

```yaml
name: "Comprehensive Google Health API Test Script"
description: "Demonstrates all supported test step attributes and operations"
stopOnError: false

steps:
  # 1. Authorization Verification
  - name: "Verify OAuth Credentials"
    action: "check_auth"
    assertStatus: [200]

  # 2. Identity & User Endpoints
  - name: "Get Identity"
    action: "identity"
    assertStatus: [200]
    assertContains:
      - "healthUserId"

  - name: "Get User Profile"
    action: "profile"
    assertStatus: [200]

  - name: "Update Profile Stride Length"
    action: "update_profile"
    body: '{"userConfiguredWalkingStrideLengthMm": "745"}'
    assertStatus: [200]

  - name: "Get User Settings"
    action: "settings"
    assertStatus: [200]

  - name: "Update Temperature Unit"
    action: "update_settings"
    body: '{"temperatureUnit": "TEMPERATURE_UNIT_FAHRENHEIT"}'
    assertStatus: [200]

  - name: "List Paired Devices"
    action: "devices"
    assertStatus: [200]

  - name: "Get IRN Profile"
    action: "irn_profile"
    assertStatus: [200]

  # 3. Data Type Operations with Range Validation & Filters
  - name: "List Steps with Page Size"
    action: "list"
    dataType: "steps"
    queryParams:
      pageSize: "25"
    assertStatus: [200]
    validateRange: true

  - name: "Daily RollUp Steps"
    action: "daily_rollup"
    dataType: "steps"
    assertStatus: [200]

  - name: "Physical RollUp Steps"
    action: "rollup"
    dataType: "steps"
    assertStatus: [200]

  - name: "Get Single Heart Rate DataPoint"
    action: "get"
    dataType: "heart-rate"
    dataPointId: "sample-dp-1"
    assertStatus: [200, 404] # Accepts 200 (found) or 404 (if ID not present)
    validateRange: false
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
| `active-energy-burned` | `v4` | kcal | ❌ No | [0, 1,000,000] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `basal-energy-burned` | `v4` | kcal | ❌ No | [0, 1,000,000] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `active-zone-minutes` | `v4` | minutes | ❌ No | [0, 1,440] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `heart-rate` | `v4` | bpm | ❌ No | [1, 300] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `daily-resting-heart-rate`| `v4` | bpm | ❌ No | [20, 250] | list, get, dailyRollUp |
| `heart-rate-variability`| `v4` | ms | ❌ No | [0, 500] | list, get, create, batchDelete |
| `weight` | `v4` | grams | ✅ Yes | [0, 1,000,000] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `height` | `v4` | millimeters | ❌ No | [0, 3,000] | list, get, create, batchDelete |
| `body-fat` | `v4` | percentage | ❌ No | [0, 100] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `oxygen-saturation` | `v4` | percentage | ❌ No | [0, 100] | list, get, create, batchDelete |
| `blood-glucose` | `v4` | mg/dL | ❌ No | [0, 900] | list, get, create, batchDelete, rollUp, dailyRollUp |
| `sleep` | `v4` | seconds | ✅ Yes | [0, 86,400] | list, get, create, batchDelete, reconcile |
| `exercise` | `v4` | seconds | ❌ No | [0, 86,400] | list, get, exportExerciseTcx |
| `electrocardiogram` | `v4` | samples | ❌ No | [0, 500] | list, get |
| `skin-temperature-sensors` | `v4beta` | celcius | ❌ No | - | list |

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

## 🔐 Authorization & Token Management (`com.google.api.client.auth.oauth2.Credential`)

The application manages OAuth tokens securely using the official Google API Client library's `com.google.api.client.auth.oauth2.Credential` backed by `FileDataStoreFactory` in the git-ignored `tokens/` directory:

- Tokens and credentials are stored in `tokens/StoredCredential` using standard Google serialization.
- Credentials are never stored in plain-text YAML files, preventing accidental commits to Git or GitHub Push Protection blocks (`GH013`).
- User settings like `healthUserId` and endpoint syntax preferences are stored separately in `config/preferences.yaml`.

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
3. Awaits the callback redirect, securely captures the authorization code, exchanges it with Google for access and refresh tokens, and saves them into the `Credential` store.

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
   - The client invokes `OAuthService.refreshAccessToken()`, which delegates to `Credential.refreshToken()`.
   - Google's token service issues a new access token (and optional rotated refresh token).
   - The Credential library updates the local data store in `tokens/` automatically.
   - The failed HTTP request is automatically retried with the new token.

---

## ⚙️ Application Configuration & Credentials

### 1. OAuth 2.0 Client Credentials (`config/client_secret.json`)

Client credentials are automatically loaded from Google Cloud Console's downloaded JSON client secrets file:

```json
{
  "web": {
    "client_id": "YOUR_CLIENT_ID.apps.googleusercontent.com",
    "client_secret": "YOUR_CLIENT_SECRET",
    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
    "token_uri": "https://oauth2.googleapis.com/token",
    "redirect_uris": ["http://localhost:8888/callback"]
  }
}
```

### 2. Application Preferences (`config/preferences.yaml`)

Stores your runtime settings, endpoint syntax, and requested scopes:

```yaml
endpointUserSyntax: me
authUri: "https://accounts.google.com/o/oauth2/v2/auth"
tokenUri: "https://oauth2.googleapis.com/token"
redirect_uri: "http://localhost:8888/callback"
apiBaseUrl: "https://health.googleapis.com"
healthUserId: "8677373576871223311" # Populated automatically by getIdentity
defaultUserId: "me"
endpointUserId: "me" # Syntax setting: "me" (uses /users/me/...) or "healthUserId" (uses /users/{healthUserId}/...)
mockMode: false
enableAllEndpoints: false

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

### 👤 Endpoint User ID Syntax (`endpointUserId`)
The `endpointUserId` preference specifies which user identifier is inserted into Google Health API request URLs:
- **`me`** (*Recommended for personal user credentials*): Formats endpoints as `/{version}/users/me/...` (e.g. `/v4beta/users/me/dataTypes/skin-temperature-sensors/dataPoints`).
- **`healthUserId`**: Formats endpoints using the user's numeric ID discovered via `getIdentity` as `/{version}/users/{healthUserId}/...` (e.g. `/v4/users/8677373576871223311/...`).

All three execution modes (Web UX, CLI Menu, and Script Runner) honor this setting when formulating API requests.

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

---

## 🏷️ Application Versioning

The test suite uses a **build-tool independent** versioning architecture (independent of Maven, Gradle, or other build tools). The single source of truth is maintained in:

```
src/main/java/com/google/health/testsuite/common/AppVersion.java
```

```java
public final class AppVersion {
    public static final String VERSION = "1.0.6";
    public static final String API_TARGET = "v4";
    public static final String RELEASE_DATE = "2026-10-17";
}
```

- **Runtime Availability**: The version is exposed via `/api/status` and `/api/version` endpoints.
- **Web UI**: Automatically displayed in the **"Preferences & OAuth"** tab (as an `App v1.0.6` pill badge and in the system information banner).
- **How to Update**: When adding new functionality or fixes, increment `AppVersion.VERSION` following Semantic Versioning (`MAJOR.MINOR.PATCH`) and document changes in the changelog below.

---

## 📋 Version History & Changelog

### Version 1.0.6 (October 17, 2026)

#### 🌟 New Features & API Enhancements
- **Standard Tests for User Endpoints & Paired Devices (Issue #34)**:
  - Added [`scripts/standard_tests_user.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/standard_tests_user.yaml) covering standard tests for user profile and configuration endpoints:
    - `Standard: User Profile (getProfile)`
    - `Standard: User Identity (getIdentity)`
    - `Standard: Irregular Rhythm Notifications (getIrnProfile)`
    - `Standard: User Settings (getSettings)`
  - Added [`scripts/standard_tests_pairedDevices.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/standard_tests_pairedDevices.yaml) covering standard tests for device management:
    - `Standard: Paired Devices (listDevices)`
  - Registered both `scripts/standard_tests_user.yaml` and `scripts/standard_tests_pairedDevices.yaml` in [`config/test_suite_manifest.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/config/test_suite_manifest.yaml) for full test suite execution.
- **Configurable `userId` Option in Test Scripts (`me`, `healthUserId`, `both`) (Issue #34)**:
  - Added top-level and per-step `userId` configuration to test YAML scripts with options `me`, `healthUserId`, or `both`.
  - Configured all 19 standard test scripts (`scripts/standard_tests-*.yaml`, `standard_tests_user.yaml`, `standard_tests_pairedDevices.yaml`, and `standard_tests_auth.yaml`) with `userId: me` by default.
  - Added automated step expansion in [`TestSuiteScript.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/model/TestSuiteScript.java) (`getExpandedSteps()`): when `userId: both` is specified, each test step is automatically cloned and executed twice—once against `(me)` and once against `(healthUserId)`.
  - Added `copy()` method to [`TestStep.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/model/TestStep.java) to support step duplication with specific user context.
  - Updated [`ScriptRunner.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/runner/ScriptRunner.java) and [`RestApiHandler.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/server/RestApiHandler.java) to process expanded steps seamlessly.
- **Dynamic User ID Resolution & Action Support**:
  - Enhanced [`HealthApiClient.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/client/HealthApiClient.java) with `getEffectiveUserId(overrideUserId)` to dynamically resolve `"me"`, `"healthUserId"`, or arbitrary user IDs against preferences and mock fallbacks.
  - Added user ID override routing to all user endpoints (`getProfile`, `getIdentity`, `getIrnProfile`, `getSettings`, `listDevices`) and data point operations (`list`, `get`, `create`, `patch`, `rollup`, `dailyrollup`, `reconcile`, `batchdelete`).
  - Added automatic rewriting of `users/me/` references in query parameters (e.g. `dataSourceFamily: "users/me/dataSources/..."`) to `users/{effectiveUserId}/...`.
  - Enhanced [`SuiteExecutionEngine.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/runner/SuiteExecutionEngine.java) to accept standard camelCase action names (`getProfile`, `getIdentity`, `getIrnProfile`, `getSettings`, `listDevices`) and propagate step-level user IDs.
- **Unit Testing**:
  - Added test coverage in [`ScriptRunnerTest.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/test/java/com/google/health/testsuite/ScriptRunnerTest.java) verifying step expansion behavior across all `userId` settings.

---

### Version 1.0.5 (October 11, 2026)

#### 🌟 New Features & API Enhancements
- **Test Suite Manifest File (Issue #28)**:
  - Added [`config/test_suite_manifest.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/config/test_suite_manifest.yaml) defining the ordered list of test script files executed when pressing "Run Full Test Suite".
  - Created [`TestSuiteManifest.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/model/TestSuiteManifest.java) to model the manifest schema, with loading/saving support in [`ConfigManager.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/config/ConfigManager.java).
  - Added `/api/manifest` GET and POST/PUT endpoints in [`RestApiHandler.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/server/RestApiHandler.java) for retrieving available scripts and saving active manifest configurations.
  - Added manifest badge and interactive "Configure Manifest" panel in the Web UI ([`index.html`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/resources/web/index.html), [`app.js`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/resources/web/app.js)) with script checkboxes, Select All, Deselect All, and Save Manifest buttons.
  - Reorganized test suites: split monolithic test scripts into modular individual datatype scripts (`scripts/standard_tests-*.yaml`) and a standalone auth verification script ([`scripts/standard_tests_auth.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/standard_tests_auth.yaml)).
  - Added "Script" column to the Test Execution Results table and recorded `scriptFile` on each [`TestResult`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/model/TestResult.java).
  - Added dynamic `DataTypeDefinition` synthesis in [`SuiteExecutionEngine.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/runner/SuiteExecutionEngine.java) to support custom and unregistered data types.
- **Real-Time Failed Tests Filtering (Issue #29)**:
  - Added **"Show Failed Only"** filter checkbox with real-time count badge to the Test Execution Results table header on the Full Test Suite tab.
  - Enabled click-to-filter directly from the red "Failed" metric summary card.
  - Real-time CSS-driven filtering hides passed rows instantly without UI lag as events stream in.
  - Added empty-state notification row when all executed tests pass while the filter is active.
  - Added persistent `suiteFilterFailedOnly` preference in [`Preferences.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/config/Preferences.java) and the Preferences tab.
- **Fix Filters on Daily VO2 Max (Issue #30)**:
  - Updated filter parameters in [`scripts/standard_tests-dailyVo2Max.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/standard_tests-dailyVo2Max.yaml) to use `daily_vo2_max.date` instead of interval times for `list` and `reconcile` operations.

---

### Version 1.0.4 (October 11, 2026)

#### 🌟 New Features & API Enhancements
- **Add Standard Tests for Activity Data Types (Issue #20)**:
  - Added individual standard test suite scripts across all activity-related Google Health API data types:
    - [`scripts/active-minutes_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/active-minutes_tests.yaml): Full test suite for `active-minutes` (`list`, `dailyrollup`, `rollup`, `reconcile`).
    - [`scripts/active-zone-minutes_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/active-zone-minutes_tests.yaml): Full test suite for `active-zone-minutes` (`list`, `dailyrollup`, `rollup`, `reconcile`).
    - [`scripts/active-energy-burned_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/active-energy-burned_tests.yaml): Full test suite for `active-energy-burned` (`list`, `dailyrollup`, `rollup`, `reconcile`).
    - [`scripts/activity-level_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/activity-level_tests.yaml): Full test suite for `activity-level` (`list`, `dailyrollup`, `rollup`, `reconcile`).
    - [`scripts/altitude_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/altitude_tests.yaml): Full test suite for `altitude` (`list`, `dailyrollup`, `rollup`, `reconcile`).
    - [`scripts/calories-in-heart-rate-zone_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/calories-in-heart-rate-zone_tests.yaml): Aggregate test suite for `calories-in-heart-rate-zone` (`dailyrollup`, `rollup`).
    - [`scripts/daily-vo2-max_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/daily-vo2-max_tests.yaml): Point-in-time test suite for `daily-vo2-max` (`list`, `reconcile`).
    - [`scripts/distance_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/distance_tests.yaml): Full test suite for `distance` (`list`, `dailyrollup`, `rollup`, `reconcile`).
    - [`scripts/floors_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/floors_tests.yaml): Full aggregate & reconcile test suite for `floors` (`dailyrollup`, `rollup`, `reconcile`).
    - [`scripts/run-vo2-max_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/run-vo2-max_tests.yaml): Full test suite for `run-vo2-max` (`list`, `dailyrollup`, `rollup`, `reconcile`).
    - [`scripts/sedentary-period_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/sedentary-period_tests.yaml): Full test suite for `sedentary-period` (`list`, `dailyrollup`, `rollup`, `reconcile`).
    - [`scripts/swim-lengths-data_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/swim-lengths-data_tests.yaml): Full test suite for `swim-lengths-data` (`list`, `dailyrollup`, `rollup`, `reconcile`).
    - [`scripts/time-in-heart-rate-zone_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/time-in-heart-rate-zone_tests.yaml): Full test suite for `time-in-heart-rate-zone` (`list`, `dailyrollup`, `rollup`, `reconcile`).
    - [`scripts/total-calories_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/total-calories_tests.yaml): Aggregate test suite for `total-calories` (`dailyrollup`, `rollup`).
    - [`scripts/vo2-max_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/vo2-max_tests.yaml): Point-in-time test suite for `vo2-max` (`list`, `reconcile`).
  - Standardized naming convention `Standard: <DisplayName> <Endpoint> (<param>)` across all test suites.
  - Tailored endpoint test definitions to each data type's API capabilities (omitting rollups for point-in-time metrics, omitting raw list for aggregate-only metrics).
- **Data Types Registry Expansion & Mock Engine Support**:
  - Registered 8 additional Google Health API data types in [`config/datatypes.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/config/datatypes.yaml): `activity-level`, `time-in-heart-rate-zone`, `swim-lengths-data`, `run-vo2-max`, `active-minutes`, `sedentary-period`, `total-calories`, and `vo2-max`.
  - Added sample data generation support in [`MockHealthBackend.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/client/MockHealthBackend.java) for the newly registered types (`minutes`, `vo2Max`, `strokeCount`, `activityLevelType`).
  - Added fallback metric field extractions in [`ValidationEngine.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/client/ValidationEngine.java).
  - Added registry unit tests in [`DataTypeRegistryTest.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/test/java/com/google/health/testsuite/DataTypeRegistryTest.java) (`testNewlyAddedDataTypes`).
- **Standard Operations Test Suite Organization**:
  - Organized [`scripts/standard_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/standard_tests.yaml) with comprehensive endpoint sections and headers for all data types.

---

### Version 1.0.3 (October 11, 2026)

#### 🌟 New Features & API Enhancements
- **Add Standard Tests for "steps" Data Type (Issue #19)**:
  - Expanded test coverage for the `steps` data type across all supported endpoints in [`scripts/standard_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/standard_tests.yaml) and [`scripts/steps_tests.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/scripts/steps_tests.yaml).
  - Adopted standardized test naming convention: `Standard: Steps <Endpoint> (<param>)`.
  - Added comprehensive step configurations for:
    - **List Endpoint**: Default, `pageSize: 5`, `pageToken`, `filter: civil_start_time`, `filter: start_time`, `dataSourceFamily: all-sources`, `dataSourceFamily: google-wearables`, `dataSourceFamily: google-sources`, and combined parameters.
    - **DailyRollUp Endpoint**: Default, `windowSizeDays: 1`, `pageSize: 25`, `pageToken`, `dataSourceFamily` variants, and combined parameters.
    - **RollUp Endpoint**: Default, `pageSize: 25`, `pageToken`, `dataSourceFamily` variants, and combined parameters.
    - **Reconcile Endpoint**: Default, `pageSize: 5`, `pageToken`, `filter` variants, `dataSourceFamily` variants, and combined parameters.
- **Reconcile Endpoint Implementation & Query Parameter Alignment**:
  - Implemented `reconcile` in [`HealthApiClient.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/client/HealthApiClient.java) as an HTTP `GET` operation (`/v4/users/{userId}/dataTypes/{dataType}/dataPoints:reconcile`) taking query parameters (`pageSize`, `pageToken`, `filter`, `dataSourceFamily`), matching the Google Health API specification.
  - Updated [`SuiteExecutionEngine.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/runner/SuiteExecutionEngine.java) to pass query parameters to `reconcileDataPoints` in both single-test and script execution modes.
  - Updated Web UX API Explorer in [`app.js`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/resources/web/app.js) to treat `reconcile` as a `GET` operation using query parameters and hiding JSON payload boxes.
  - Updated CLI runner operations prompt in [`CliMenuRunner.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/runner/CliMenuRunner.java) to include `reconcile`.
- **YAML Script Syntax Error Reporting**:
  - Enhanced [`RestApiHandler.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/server/RestApiHandler.java) to return detailed YAML parser/syntax error messages in `message` and `errorMessage` response fields when script files contain malformed syntax.
- **Data Types Registry Configuration**:
  - Updated [`config/datatypes.yaml`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/config/datatypes.yaml) to enable `reconcile: true` for the `steps` data type and align supported endpoints.

---

### Version 1.0.2 (October 11, 2026)

#### 🐛 Bug Fixes & API Enhancements
- **Fix "Standard: DailyRollUp Steps" Failure (Issue #15)**:
  - Added request payload `body` in `scripts/standard_tests.yaml` defining a valid `CivilTimeInterval` (`start` and `end` with `date` and `time` objects) and `windowSizeDays: 1`, preventing `HTTP 400 Invalid argument in request`.
  - Updated `TestStep` with custom `BodyDeserializer` to ensure the script execution engine accurately deserializes and transmits the payload value (`body`), supporting both quoted JSON strings and YAML object mappings.
  - Updated Web UX API Explorer in `app.js` to pre-populate valid `CivilTimeInterval` and `windowSizeDays: 1` template payloads when `dailyrollup` is selected.
- **Fix "Standard: RollUp Steps" Failure (Issue #16)**:
  - Added request payload `body` in `scripts/standard_tests.yaml` (and `scripts/sample_suite.yaml`) defining a valid RFC 3339 physical time `range` (`startTime` and `endTime`) and `windowSize: "3600s"`, preventing `HTTP 400 Invalid time range`.
  - Updated Web UX API Explorer in `app.js` to pre-populate valid RFC 3339 `range` and `windowSize: "3600s"` template payloads when `rollup` is selected.
- **Validation Engine Enhancements**:
  - Updated `ValidationEngine` to parse and validate `rollupDataPoints` arrays in addition to standard `dataPoints` arrays for range check assertions.
  - Updated `MockHealthBackend` to include `rollupDataPoints` array in mock mode.

---

### Version 1.0.1 (October 10, 2026)

#### 🌟 New Features & Enhancements
- **Script Runner Detailed Failure Reporting (Issue #14)**:
  - **REST API (`POST /api/test/script`)**: When script test steps fail, the JSON response now returns:
    - `failedTestNames`: Array containing the names of all failing test steps (e.g. `["Standard: Update Settings (combined)"]`).
    - `failedTests`: Detailed array of failing items containing `testName`, `endpoint`, `dataType`, `statusCode`, `latencyMs`, and `message`.
    - `message`: Formatted summary string explicitly identifying the failing test names (`"One or more script tests failed: <test-name>"`).
    - Execution metrics: `total`, `passed`, `failed`, and `durationMs`.
  - **CLI Script Runner (Mode 3)**: At the end of execution, if any tests failed, the execution summary now prints a highlighted `FAILED TESTS (<count>)` section listing each test step name, HTTP status code, and failure message for immediate debugging without scrolling.
  - **`ScriptRunner.runScriptWithDetails(File)`**: Added programmatic API returning a structured `ScriptRunResult` object containing complete execution statistics and failure lists.

---

### Version 1.0.0 (October 10, 2026) - *Initial Release*

#### 🌟 New Features
- **Three Execution Modes**:
  - **Mode 1 (Interactive CLI Menu)**: ANSI-color interactive terminal interface with menus for preferences, tokens, single data types, full test suite, identity/profile, and script running.
  - **Mode 2 (Modern Web UX)**: Dark-theme glassmorphism dashboard running on embedded HTTP server (`--web [port]`) with real-time test execution streaming.
  - **Mode 3 (Non-Interactive Script Runner)**: Headless YAML/JSON script executor (`--script <path>`) with status and min/max range assertions, exit code `0` on success and `1` on failure for CI/CD pipelines.
- **Google Health API v4 Coverage**:
  - Full support for 19 health/fitness data types: `steps`, `distance`, `heart-rate`, `daily-resting-heart-rate`, `heart-rate-variability`, `electrocardiogram`, `active-energy-burned`, `basal-energy-burned`, `active-zone-minutes`, `altitude`, `body-weight`, `body-height`, `body-fat-percentage`, `blood-oxygen-saturation`, `blood-glucose`, `skin-temperature`, `sleep-sessions`, `exercise-workouts`, `paired-devices`.
  - Identity, Profile, Irregular Rhythm Notifications (IRN), and Settings endpoints (`get_identity`, `get_profile`, `update_profile`, `get_irn_profile`, `get_settings`, `update_settings`, `list_devices`).
  - Supported operations: `list`, `get`, `create`, `patch`, `rollup`, `dailyrollup`, `reconcile`, `exportexercisetcx`, and `batchdelete`.
- **OAuth 2.0 Integration**:
  - Credential persistence via `com.google.api.client.auth.oauth2.Credential` and `FileDataStoreFactory`.
  - Automatic token refresh before expiration and on HTTP 401 Unauthorized errors.
  - Dynamic scope selector in Web UX with real-time OAuth parameter string generation.
  - One-click token refresh and countdown timers in Web UX.
- **Data Types Catalog & Validation**:
  - Single declarative catalog in `config/datatypes.yaml` with min/max range constraints.
  - Interactive "Add Data Type" feature in Web UX to register new data types into `datatypes.yaml`.
  - Validation engine checking numerical response boundaries across all test modes.
- **Web UX Enhancements**:
  - Real-time streaming test execution via NDJSON chunked HTTP responses.
  - Configurable scrollable result window with uniform row heights and sticky table header.
  - Dynamic user syntax switcher between `/users/me` and `/users/{healthUserId}`.
  - Interactive API Explorer with live payload editor and one-click copyable `curl` command generator.
  - Build-tool independent version display in the "Preferences & OAuth" tab.

#### 🐛 Bug Fixes & Refinements
- **Test Runner Results Display**: Changed table column from showing endpoint verbs (`ENDPOINT`) to descriptive test names (`TEST`) for clear identification of executed tests.
- **Status Badges**: Removed redundant "HTTP" prefix in execution status badges (e.g. `200` instead of `HTTP 200`).
- **Scroll Box Height Alignment**: Fixed calculation for the configurable scrollable box rows in the Test Suite tab to ensure the visible count matches user preferences exactly.
- **Action Aliases**: Added aliases for `daily_rollup` / `dailyrollup` and `roll_up` / `rollup` in `SuiteExecutionEngine`.
- **Script Validation**: Fixed syntax quoting in `scripts/standard_tests.yaml` and ensured strict 404 error handling if script file is missing.

