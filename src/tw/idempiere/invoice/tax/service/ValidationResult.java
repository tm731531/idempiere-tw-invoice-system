package tw.idempiere.invoice.tax.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationResult {
    private final boolean valid;
    private final String errorMessage;
    private final List<String> warnings;

    private ValidationResult(boolean valid, String errorMessage, List<String> warnings) {
        this.valid = valid;
        this.errorMessage = errorMessage;
        this.warnings = Collections.unmodifiableList(warnings);
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, null, new ArrayList<>());
    }

    public static ValidationResult error(String message) {
        return new ValidationResult(false, message, new ArrayList<>());
    }

    public ValidationResult withWarning(String warning) {
        List<String> w = new ArrayList<>(this.warnings);
        w.add(warning);
        return new ValidationResult(this.valid, this.errorMessage, w);
    }

    public boolean isValid() { return valid; }
    public String getErrorMessage() { return errorMessage; }
    public List<String> getWarnings() { return warnings; }
    public boolean hasWarning() { return !warnings.isEmpty(); }
}
