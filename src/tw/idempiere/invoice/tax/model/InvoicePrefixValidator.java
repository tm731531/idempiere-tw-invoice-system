/******************************************************************************
 * Taiwan Invoice Tax System for iDempiere
 * Copyright (C) Taiwan iDempiere Community. All Rights Reserved.
 * License: GNU General Public License version 2
 *****************************************************************************/
package tw.idempiere.invoice.tax.model;

/**
 * Pure-Java validation logic for {@link MInvoicePrefix} fields.
 *
 * <p>This class contains <em>no iDempiere dependencies</em>, which means it
 * can be instantiated and tested in a plain JUnit environment without an OSGi
 * runtime or a database connection.</p>
 *
 * <p>The validation rules here are the canonical source of truth; the
 * {@code MInvoicePrefix#beforeSave(boolean)} lifecycle hook delegates to
 * {@link #validate(String, int, int, int, boolean)} to avoid duplicating
 * logic.</p>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public final class InvoicePrefixValidator {

    /** Not instantiable — use the static methods directly. */
    private InvoicePrefixValidator() {
    }

    // -----------------------------------------------------------------------
    // ValidationResult
    // -----------------------------------------------------------------------

    /**
     * Immutable outcome of a call to {@link #validate}.
     */
    public static final class ValidationResult {

        /** {@code true} when all checks passed */
        public final boolean valid;

        /** Human-readable error message when {@code valid == false}; {@code null} on success */
        public final String errorMessage;

        /**
         * The resolved {@code CurrentNumber} after the initialisation step.
         *
         * <p>When {@code valid == true} and the call was made with
         * {@code newRecord = true} and {@code currentNumber == 0}, this value
         * equals {@code startNumber}.  In all other success cases it equals
         * the {@code currentNumber} that was passed in.</p>
         */
        public final int resolvedCurrentNumber;

        ValidationResult(boolean valid, String errorMessage, int resolvedCurrentNumber) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.resolvedCurrentNumber = resolvedCurrentNumber;
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code code} is a valid invoice prefix code.
     *
     * <p>A valid prefix code is exactly two ASCII uppercase letters (A–Z).</p>
     *
     * @param code the code to test (may be {@code null})
     * @return {@code true} if valid
     */
    public static boolean isValidPrefixCode(String code) {
        if (code == null) {
            return false;
        }
        return code.matches("[A-Z]{2}");
    }

    /**
     * Returns {@code true} if the combination of {@code startNumber} and
     * {@code endNumber} represents a valid, non-empty range.
     *
     * <p>Rules:
     * <ul>
     *   <li>{@code startNumber} must be {@literal >=} 1</li>
     *   <li>{@code endNumber}   must be {@literal >=} startNumber</li>
     * </ul>
     * </p>
     *
     * @param startNumber first number in the range
     * @param endNumber   last  number in the range
     * @return {@code true} if valid
     */
    public static boolean isValidNumberRange(int startNumber, int endNumber) {
        return startNumber >= 1 && endNumber >= startNumber;
    }

    /**
     * Runs the full set of field-level validations that mirror the
     * {@code MInvoicePrefix#beforeSave(boolean)} lifecycle hook.
     *
     * <p>Validation order:
     * <ol>
     *   <li>PrefixCode must pass {@link #isValidPrefixCode(String)}</li>
     *   <li>StartNumber must be {@literal >=} 1</li>
     *   <li>EndNumber must be {@literal >=} StartNumber</li>
     *   <li>If {@code newRecord == true} and {@code currentNumber == 0},
     *       the resolved current number is set to {@code startNumber}</li>
     * </ol>
     * </p>
     *
     * @param prefixCode    proposed prefix code
     * @param startNumber   proposed start number
     * @param endNumber     proposed end number
     * @param currentNumber proposed current number (0 = uninitialised)
     * @param newRecord     {@code true} for an INSERT, {@code false} for UPDATE
     * @return a {@link ValidationResult} describing the outcome
     */
    public static ValidationResult validate(
            String prefixCode,
            int startNumber,
            int endNumber,
            int currentNumber,
            boolean newRecord) {

        // Rule 1: PrefixCode
        if (!isValidPrefixCode(prefixCode)) {
            return new ValidationResult(false,
                "PrefixCode must be exactly 2 uppercase letters (A-Z). Got: " + prefixCode,
                currentNumber);
        }

        // Rule 2: StartNumber
        if (startNumber < 1) {
            return new ValidationResult(false,
                "StartNumber must be >= 1. Got: " + startNumber,
                currentNumber);
        }

        // Rule 3: EndNumber >= StartNumber
        if (endNumber < startNumber) {
            return new ValidationResult(false,
                "EndNumber (" + endNumber + ") must be >= StartNumber (" + startNumber + ").",
                currentNumber);
        }

        // Rule 4: Initialise CurrentNumber for new records
        int resolved = currentNumber;
        if (newRecord && currentNumber == 0) {
            resolved = startNumber;
        }

        return new ValidationResult(true, null, resolved);
    }
}
