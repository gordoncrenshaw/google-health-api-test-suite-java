package com.google.health.testsuite.model;

/**
 * Result of validating data values against min and max thresholds.
 */
public class ValidationResult {

    private final boolean valid;
    private final String message;
    private final Double extractedValue;
    private final Double minValue;
    private final Double maxValue;

    public ValidationResult(boolean valid, String message, Double extractedValue, Double minValue, Double maxValue) {
        this.valid = valid;
        this.message = message;
        this.extractedValue = extractedValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public static ValidationResult pass(String message, Double extractedValue, Double min, Double max) {
        return new ValidationResult(true, message, extractedValue, min, max);
    }

    public static ValidationResult fail(String message, Double extractedValue, Double min, Double max) {
        return new ValidationResult(false, message, extractedValue, min, max);
    }

    public static ValidationResult skipped(String reason) {
        return new ValidationResult(true, "Validation skipped: " + reason, null, null, null);
    }

    public boolean isValid() {
        return valid;
    }

    public String getMessage() {
        return message;
    }

    public Double getExtractedValue() {
        return extractedValue;
    }

    public Double getMinValue() {
        return minValue;
    }

    public Double getMaxValue() {
        return maxValue;
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "valid=" + valid +
                ", message='" + message + '\'' +
                ", extractedValue=" + extractedValue +
                ", range=[" + minValue + ", " + maxValue + "]" +
                '}';
    }
}
