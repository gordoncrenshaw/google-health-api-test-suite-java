# Workspace Guidelines & Workflows

This document defines standard instructions and operational workflows for Antigravity (and all AI pair programmers) working on the `google-health-api-test-suite-java` project.

---

## 🚀 GitHub Push & Release Workflow

Whenever asked to push changes to GitHub, cut a release, or resolve an issue, **always** follow this 5-step checklist:

### 1. Branching Strategy
- **Never push directly to `master`** unless explicitly requested by the user.
- **For bug fixes and feature enhancements**: Create a branch named:
  `feature/issue-<number>-<brief-description>` or `fix/issue-<number>-<brief-description>`
- **For version releases**: Create a branch named:
  `release/<version>` (e.g., `release/1.0.1`)
- Base new branches off the latest `origin/master`.

### 2. Validation & Build Verification
- **Run the test suite**: Execute `mvn test`. Ensure all unit and integration tests pass with 0 failures before committing.
- **Rebuild the shaded executable JAR**: Execute `mvn clean package -DskipTests` to ensure `target/health-api-testsuite.jar` is updated.
- If the background web server daemon is running, restart it with the updated JAR so live testing reflects the latest code.

### 3. Versioning & Release Documentation
- When introducing new features, fixes, or releases:
  - **Single Source of Truth**: Update `AppVersion.VERSION` in [`src/main/java/com/google/health/testsuite/common/AppVersion.java`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/src/main/java/com/google/health/testsuite/common/AppVersion.java) following Semantic Versioning (`MAJOR.MINOR.PATCH`).
  - **Release Notes / Changelog**: Add the release notes section under `## 📋 Version History & Changelog` in [`README.md`](file:///Users/gordoncrenshaw/development/codelibrary/java/google-health-api-test-suite-java/README.md) documenting:
    - Target issue number and title.
    - Summary of new features, bug fixes, API endpoint additions, or UI enhancements.

### 4. Clean Staging, Commits & Push
- **Review git status**: Inspect `git status` and `git diff` before adding files.
- **Do NOT stage runtime or sensitive files**:
  - `tokens/` (OAuth credential store)
  - `client_secret.json` (Google Cloud OAuth secrets)
  - Local runtime changes in `config/preferences.yaml`
- **Commit Message Format**:
  - Provide a concise, descriptive commit title.
  - Reference the issue number (e.g., `(Closes #14)`).
  - Example: `git commit -m "List failed tests and failure details in script runner response (Closes #14) - Release v1.0.1"`
- **Push to Remote**: Push the branch to GitHub:
  `git push -u origin <branch-name>`

### 5. GitHub Issue & PR Updates
- **Update GitHub Issue**: Post a clear comment on the associated GitHub issue detailing:
  - Branch and commit hash where the change is located.
  - Bulleted summary of the changes made.
- **Close the Issue**: Mark the issue as closed/completed on GitHub.
- **Provide PR Link**: In the chat response, provide the GitHub link to open/merge the Pull Request into `master` (e.g. `https://github.com/gordoncrenshaw/google-health-api-test-suite-java/pull/new/<branch-name>`).

---

## 🛠️ General Repository Guidelines

- **Build-Tool Independent Versioning**: Application version is declared in `com.google.health.testsuite.common.AppVersion`, not tightly coupled to Maven `pom.xml`.
- **Formatting & Links**: Always format file and code symbols using GitHub-style markdown clickable links with the `file://` scheme.
- **Preserve Documentation Integrity**: Do not remove existing comments or docstrings unrelated to current code edits.
