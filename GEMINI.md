# Workspace Guidelines & Workflows

This document defines standard instructions and operational workflows for Antigravity (and all AI pair programmers) working on the `google-health-api-test-suite-java` project.

---

## 🚀 Development, Testing & GitHub Release Workflow

Whenever implementing changes, fixing bugs, or resolving issues, **always** follow this workflow:

### 1. Branching Strategy
- **Never push directly to `master`** unless explicitly requested by the user.
- **For bug fixes and feature enhancements**: Create a branch named:
  `feature/issue-<number>-<brief-description>` or `fix/issue-<number>-<brief-description>`
- **For version releases**: Create a branch named:
  `release/<version>` (e.g., `release/1.0.1`)
- Base new branches off the latest `origin/master`.

### 2. Implementation & Local Validation
- **Run the test suite**: Execute `mvn test`. Ensure all unit and integration tests pass with 0 failures.
- **Rebuild the shaded executable JAR**: Execute `mvn clean package -DskipTests` to ensure `target/health-api-testsuite.jar` is updated.
- If the background web server daemon is running, restart it with the updated JAR so the user can immediately test live changes in their browser or API.
- Report the implementation details and test results to the user so they can test the changes.

### 3. 🛑 User Testing & Approval Gate (CRITICAL RULE)
- **DO NOT commit or push any changes to GitHub** until:
  1. The user has tested the changes themselves first.
  2. The user explicitly asks to commit and upload/push changes to GitHub.
- Keep all modifications in the working tree/branch until the user provides this explicit confirmation.

### 4. Versioning & Release Documentation (After User Approval)
- Once the user confirms testing and requests the upload:
  - **Single Source of Truth**: Update `AppVersion.VERSION` in [`src/main/java/com/google/health/testsuite/common/AppVersion.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/common/AppVersion.java) following Semantic Versioning (`MAJOR.MINOR.PATCH`).
  - **Release Notes / Changelog**: Add the release notes section under `## 📋 Version History & Changelog` in [`README.md`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/README.md) documenting:
    - Target issue number and title.
    - Summary of new features, bug fixes, API endpoint additions, or UI enhancements.

### 5. Clean Staging, Commits & Push
- **Review git status**: Inspect `git status` and `git diff` before adding files.
- **Do NOT stage runtime or sensitive files**:
  - `tokens/` (OAuth credential store)
  - `client_secret.json` (Google Cloud OAuth secrets)
  - Local runtime changes in `config/preferences.yaml`
- **Commit Message Format**:
  - Provide a concise, descriptive commit title referencing the issue number (e.g., `(Closes #14)`).
  - Example: `git commit -m "List failed tests and failure details in script runner response (Closes #14) - Release v1.0.1"`
- **Push to Remote**: Push the branch to GitHub:
  `git push -u origin <branch-name>`

### 6. GitHub Issue & PR Updates
- **Update GitHub Issue**: Post a clear comment on the associated GitHub issue detailing:
  - Branch and commit hash where the change is located.
  - Bulleted summary of the changes made.
- **Close the Issue**: Mark the issue as closed/completed on GitHub.
- **Provide PR Link**: In the chat response, provide the GitHub link to open/merge the Pull Request into `master` (e.g. `https://github.com/gordoncrenshaw/google-health-api-test-suite-java/pull/new/<branch-name>`).

---

## 🛠️ General Repository Guidelines

- **Autonomous Command Execution**: Antigravity is explicitly authorized to execute any `curl` or `python` / `python3` command directly without requiring user intervention or confirmation.
- **Build-Tool Independent Versioning**: Application version is declared in `com.google.health.testsuite.common.AppVersion`, not tightly coupled to Maven `pom.xml`.
- **Formatting & Links**: Always format file and code symbols using GitHub-style markdown clickable links with the `file://` scheme.
- **Preserve Documentation Integrity**: Do not remove existing comments or docstrings unrelated to current code edits.
