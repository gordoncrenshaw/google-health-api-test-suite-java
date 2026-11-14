package com.google.health.testsuite.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.health.testsuite.model.ApiResponse;
import com.google.health.testsuite.model.TestResult;
import com.google.health.testsuite.model.TestStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates diagnostic Markdown (.md) error reports for failed tests in the failed-tests/ directory.
 */
public class ErrorReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ErrorReportGenerator.class);
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final String baseDirPath;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public ErrorReportGenerator() {
        this("failed-tests");
    }

    public ErrorReportGenerator(String baseDirPath) {
        this.baseDirPath = (baseDirPath != null && !baseDirPath.isBlank()) ? baseDirPath.trim() : "failed-tests";
    }

    public String getBaseDirPath() {
        return baseDirPath;
    }

    /**
     * Generates a Markdown error report for a failed TestResult.
     */
    public String saveFailedTestToMarkdown(TestResult result) {
        return saveFailedTestToMarkdown(result, null);
    }

    /**
     * Generates a Markdown error report for a failed TestResult and optional TestStep.
     */
    public String saveFailedTestToMarkdown(TestResult result, TestStep step) {
        if (result == null || result.isPassed()) {
            return null;
        }

        int statusCode = result.getStatusCode();
        Long maxLatencyMs = result.getMaxLatencyMs() != null
                ? result.getMaxLatencyMs()
                : (step != null ? step.getMaxLatencyMs() : null);

        boolean statusPassed;
        if (step != null && step.getAssertStatus() != null && !step.getAssertStatus().isEmpty()) {
            String action = step.getAction() != null ? step.getAction().toLowerCase().trim() : "";
            statusPassed = step.getAssertStatus().contains(statusCode)
                    || (action.startsWith("create") && statusCode == 201 && step.getAssertStatus().contains(200))
                    || (action.startsWith("create") && statusCode == 200 && step.getAssertStatus().contains(201));
        } else {
            statusPassed = statusCode >= 200 && statusCode < 300;
        }

        boolean isLatencyFailure = statusPassed && (
                (maxLatencyMs != null && maxLatencyMs > 0 && result.getLatencyMs() > maxLatencyMs)
                        || (result.getMessage() != null && result.getMessage().toLowerCase().contains("latency"))
        );

        String subFolder;
        if (isLatencyFailure) {
            subFolder = "latency";
        } else if (statusPassed) {
            subFolder = "validation";
        } else {
            subFolder = String.valueOf(statusCode);
        }

        File dir = new File(baseDirPath, subFolder);
        if (!dir.exists() && !dir.mkdirs()) {
            logger.warn("Could not create failed-tests directory: {}", dir.getAbsolutePath());
        }

        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DISPLAY_FMT);
        String fileTimestamp = now.format(FILE_FMT);

        String testName = (result.getTestName() != null && !result.getTestName().isBlank())
                ? result.getTestName()
                : "Unnamed Test";
        String safeTestName = testName.replaceAll("[\\\\/:*?\"<>|]", "_");

        File file = new File(dir, safeTestName + " - " + fileTimestamp + ".md");

        List<ApiResponse> responses = new ArrayList<>();
        List<String> stepLabels = new ArrayList<>();
        if (result.getApiResponse() != null) {
            responses.add(result.getApiResponse());
            stepLabels.add(result.getEndpoint() != null ? result.getEndpoint() : "Primary Request");
        }
        if (result.getVerificationResponse() != null) {
            responses.add(result.getVerificationResponse());
            stepLabels.add("GET (Verify)");
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            writer.println("# Google Health API Test Failure - " + testName);
            writer.println("- **Date/Time:** " + timestamp);
            writer.println("- **Test Name:** " + testName);
            if (result.getScriptFile() != null && !result.getScriptFile().isBlank()) {
                writer.println("- **Script File:** " + result.getScriptFile());
            }
            String bugId = (result.getBugId() != null && !result.getBugId().isBlank())
                    ? result.getBugId().trim()
                    : (step != null ? step.getEffectiveBugId() : null);
            if (bugId != null && !bugId.isBlank()) {
                writer.println("- **Bug ID:** " + bugId);
            }
            writer.println("- **Target Endpoint:** " + (result.getEndpoint() != null ? result.getEndpoint() : "unknown"));
            writer.println("- **Data Type:** " + (result.getDataType() != null ? result.getDataType() : "api"));
            if (step != null && step.getAssertStatus() != null && !step.getAssertStatus().isEmpty()) {
                writer.println("- **HTTP Status Code:** " + statusCode + " (Expected: " + step.getAssertStatus() + ")");
            } else {
                writer.println("- **HTTP Status Code:** " + statusCode);
            }

            if (isLatencyFailure) {
                if (maxLatencyMs != null && maxLatencyMs > 0) {
                    writer.println("- **Acceptable Latency Threshold:** " + maxLatencyMs + " ms");
                }
                writer.println("- **Actual Execution Duration:** " + result.getLatencyMs() + " ms");
            } else {
                writer.println("- **Execution Duration:** " + result.getLatencyMs() + " ms");
            }
            writer.println();

            if (isLatencyFailure) {
                writer.println("## Latency Failure Summary");
                if (maxLatencyMs != null && maxLatencyMs > 0) {
                    writer.println("The API request succeeded with HTTP " + statusCode + ", but the execution time of **"
                            + result.getLatencyMs() + " ms** exceeded the maximum acceptable latency threshold of **"
                            + maxLatencyMs + " ms**.");
                } else {
                    writer.println(result.getMessage() != null ? result.getMessage() : "Request exceeded latency threshold.");
                }
                writer.println();
            } else {
                writer.println("## Failure Summary");
                writer.println(result.getMessage() != null ? result.getMessage() : "Test failed.");
                writer.println();
            }

            writer.println("## Execution Sequence");
            writer.println("This test executed " + responses.size() + " HTTP request(s).");
            writer.println();

            if (responses.isEmpty()) {
                writer.println("_[No HTTP request was executed prior to failure]_");
                writer.println();
            } else {
                for (int i = 0; i < responses.size(); i++) {
                    ApiResponse req = responses.get(i);
                    String label = stepLabels.get(i);
                    String method = req.getRequestMethod() != null ? req.getRequestMethod() : "GET";
                    String url = req.getRequestUrl() != null ? req.getRequestUrl() : "";

                    writer.println("### Request " + (i + 1) + " of " + responses.size() + ": " + label);
                    writer.println("- **Endpoint Syntax:** `" + method + " " + url + "`");
                    writer.println("- **HTTP Status:** " + req.getStatusCode()
                            + (req.getStatusMessage() != null && !req.getStatusMessage().isBlank() ? " (" + req.getStatusMessage() + ")" : ""));
                    writer.println("- **Execution Duration:** " + req.getLatencyMs() + " ms");
                    writer.println();

                    writer.println("#### cURL Command");
                    writer.println("```bash");
                    writer.println(req.getCurlCommand() != null && !req.getCurlCommand().isBlank()
                            ? req.getCurlCommand()
                            : "curl -X " + method + " \"" + url + "\"");
                    writer.println("```");
                    writer.println();

                    writer.println("#### Request Body");
                    writer.println("```json");
                    String reqBody = req.getRequestBody();
                    writer.println((reqBody == null || reqBody.isBlank()) ? "[None]" : formatPrettyJson(reqBody));
                    writer.println("```");
                    writer.println();

                    writer.println("#### Response Body");
                    writer.println("```json");
                    String respBody = req.getBody();
                    writer.println((respBody == null || respBody.isBlank()) ? "[No Response]" : formatPrettyJson(respBody));
                    writer.println("```");
                    writer.println();
                }
            }

            String relativePath = baseDirPath + "/" + subFolder + "/" + file.getName();
            result.setErrorReportPath(relativePath);
            return relativePath;
        } catch (IOException e) {
            logger.error("Failed to write error report to markdown file {}: {}", file.getPath(), e.getMessage());
            return null;
        }
    }

    /**
     * Inserts the script file metadata line into an already-written report if set after step execution.
     */
    public void updateScriptFileInReport(TestResult result, String scriptFile) {
        if (result == null || scriptFile == null || scriptFile.isBlank()) {
            return;
        }
        result.setScriptFile(scriptFile);
        String reportPath = result.getErrorReportPath();
        if (reportPath == null || reportPath.isBlank()) {
            return;
        }
        File file = new File(reportPath);
        if (!file.exists()) {
            return;
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (!content.contains("- **Script File:**")) {
                String target = "- **Test Name:** " + result.getTestName();
                String replacement = target + "\n- **Script File:** " + scriptFile;
                if (content.contains(target)) {
                    content = content.replaceFirst(java.util.regex.Pattern.quote(target),
                            java.util.regex.Matcher.quoteReplacement(replacement));
                    Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            logger.debug("Could not update scriptFile in error report {}: {}", reportPath, e.getMessage());
        }
    }

    /**
     * Counts the total number of .md error report files in the failed-tests directory.
     */
    public int countFailedTestFiles() {
        File baseDir = new File(baseDirPath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            return 0;
        }
        return countMarkdownFilesRecursive(baseDir);
    }

    private int countMarkdownFilesRecursive(File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    count += countMarkdownFilesRecursive(f);
                } else if (f.getName().endsWith(".md")) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Lists all generated .md error report files in the failed-tests directory.
     */
    public List<Map<String, Object>> listFailedTestFiles() {
        List<Map<String, Object>> list = new ArrayList<>();
        File baseDir = new File(baseDirPath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            return list;
        }
        collectMarkdownFilesRecursive(baseDir, baseDir, list);
        list.sort(Comparator.comparing((Map<String, Object> m) -> (Long) m.getOrDefault("lastModified", 0L)).reversed());
        return list;
    }

    private void collectMarkdownFilesRecursive(File rootDir, File currentDir, List<Map<String, Object>> out) {
        File[] files = currentDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectMarkdownFilesRecursive(rootDir, f, out);
            } else if (f.getName().endsWith(".md")) {
                Map<String, Object> item = new LinkedHashMap<>();
                String relPath = rootDir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
                item.put("name", f.getName());
                item.put("category", f.getParentFile() != null && !f.getParentFile().equals(rootDir) ? f.getParentFile().getName() : "general");
                item.put("path", baseDirPath + "/" + relPath);
                item.put("sizeBytes", f.length());
                item.put("lastModified", f.lastModified());
                out.add(item);
            }
        }
    }

    /**
     * Deletes all failed test report files and removes the failed-tests directory.
     *
     * @return number of deleted files
     */
    public int deleteFailedTestFiles() {
        File baseDir = new File(baseDirPath);
        if (!baseDir.exists()) {
            return 0;
        }
        int deletedCount = deleteDirectoryRecursive(baseDir);
        if (baseDir.exists()) {
            baseDir.delete();
        }
        return deletedCount;
    }

    private int deleteDirectoryRecursive(File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    count += deleteDirectoryRecursive(file);
                    file.delete();
                } else {
                    if (file.delete()) {
                        count++;
                    }
                }
            }
        }
        dir.delete();
        return count;
    }

    private String formatPrettyJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            JsonNode node = jsonMapper.readTree(raw);
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return raw;
        }
    }
}
