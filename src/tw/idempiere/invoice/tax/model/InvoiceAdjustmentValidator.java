/******************************************************************************
 * Taiwan Invoice Tax System for iDempiere
 * Copyright (C) Taiwan iDempiere Community. All Rights Reserved.
 * License: GNU General Public License version 2
 *****************************************************************************/
package tw.idempiere.invoice.tax.model;

import java.math.BigDecimal;

/**
 * Pure-Java validation logic for {@link MInvoiceAdjustment} fields.
 *
 * <p>This class contains <em>no iDempiere dependencies</em>, which means it
 * can be instantiated and tested in a plain JUnit environment without an OSGi
 * runtime or a database connection.</p>
 *
 * <p>Validation rules:
 * <ol>
 *   <li>{@code TW_InvoicePrefixMap_ID} must be &gt; 0</li>
 *   <li>{@code AdjustmentType} must be one of {@code RETURN} or {@code ALLOWANCE}</li>
 *   <li>{@code AdjustmentAmount} must be &gt; 0</li>
 *   <li>{@code InputTaxAmount} must be &gt;= 0 and &lt;= {@code AdjustmentAmount}</li>
 *   <li>{@code RequiredReportingPeriod} must be a valid {@code YYYYMM} string
 *       where the month is one of: 01, 03, 05, 07, 09, 11 (first month of a
 *       bimonthly window)</li>
 * </ol>
 * </p>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public final class InvoiceAdjustmentValidator {

    /** Not instantiable — use the static methods directly. */
    private InvoiceAdjustmentValidator() {
    }

    // -----------------------------------------------------------------------
    // AdjustmentType constants (mirrors MInvoiceAdjustment)
    // -----------------------------------------------------------------------

    /** Adjustment type: goods return (退回) */
    public static final String ADJUSTMENTTYPE_RETURN     = "RETURN";

    /** Adjustment type: price allowance / debit note (折讓) */
    public static final String ADJUSTMENTTYPE_ALLOWANCE  = "ALLOWANCE";

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

        ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code adjustmentType} is a recognised value.
     *
     * @param adjustmentType the type string to test (may be {@code null})
     * @return {@code true} if valid
     */
    public static boolean isValidAdjustmentType(String adjustmentType) {
        return ADJUSTMENTTYPE_RETURN.equals(adjustmentType)
            || ADJUSTMENTTYPE_ALLOWANCE.equals(adjustmentType);
    }

    /**
     * Returns {@code true} if {@code period} is a valid bimonthly reporting
     * period identifier.
     *
     * <p>Format: {@code YYYYMM} where {@code MM} must be one of
     * {@code 01, 03, 05, 07, 09, 11} (the first month of each bimonthly
     * window).</p>
     *
     * @param period the period string to test (may be {@code null})
     * @return {@code true} if valid
     */
    public static boolean isValidReportingPeriod(String period) {
        if (period == null || period.length() != 6) {
            return false;
        }
        if (!period.matches("\\d{6}")) {
            return false;
        }
        int year  = Integer.parseInt(period.substring(0, 4));
        int month = Integer.parseInt(period.substring(4, 6));
        if (year < 2000) {
            return false;
        }
        // Month must be the first month of a bimonthly window
        return month == 1 || month == 3 || month == 5
            || month == 7 || month == 9 || month == 11;
    }

    /**
     * Runs the full set of field-level validations that mirror
     * {@code MInvoiceAdjustment#beforeSave(boolean)}.
     *
     * @param prefixMapId            value of {@code TW_InvoicePrefixMap_ID} (must be &gt; 0)
     * @param adjustmentType         value of {@code AdjustmentType}
     * @param adjustmentAmount       value of {@code AdjustmentAmount} (must be &gt; 0)
     * @param inputTaxAmount         value of {@code InputTaxAmount} (must be &gt;= 0)
     * @param requiredReportingPeriod value of {@code RequiredReportingPeriod}
     * @return a {@link ValidationResult} describing the outcome
     */
    public static ValidationResult validate(
            int prefixMapId,
            String adjustmentType,
            BigDecimal adjustmentAmount,
            BigDecimal inputTaxAmount,
            String requiredReportingPeriod) {

        // Rule 1: TW_InvoicePrefixMap_ID
        if (prefixMapId <= 0) {
            return new ValidationResult(false,
                "TW_InvoicePrefixMap_ID must be > 0. Got: " + prefixMapId);
        }

        // Rule 2: AdjustmentType
        if (!isValidAdjustmentType(adjustmentType)) {
            return new ValidationResult(false,
                "AdjustmentType must be RETURN or ALLOWANCE. Got: " + adjustmentType);
        }

        // Rule 3: AdjustmentAmount > 0
        if (adjustmentAmount == null || adjustmentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return new ValidationResult(false,
                "AdjustmentAmount must be > 0. Got: " + adjustmentAmount);
        }

        // Rule 4: InputTaxAmount >= 0 and <= AdjustmentAmount
        if (inputTaxAmount == null || inputTaxAmount.compareTo(BigDecimal.ZERO) < 0) {
            return new ValidationResult(false,
                "InputTaxAmount must be >= 0. Got: " + inputTaxAmount);
        }
        if (inputTaxAmount.compareTo(adjustmentAmount) > 0) {
            return new ValidationResult(false,
                "InputTaxAmount (" + inputTaxAmount + ") must be <= AdjustmentAmount ("
                    + adjustmentAmount + ").");
        }

        // Rule 5: RequiredReportingPeriod format
        if (!isValidReportingPeriod(requiredReportingPeriod)) {
            return new ValidationResult(false,
                "RequiredReportingPeriod must be YYYYMM where MM is 01,03,05,07,09 or 11. Got: "
                    + requiredReportingPeriod);
        }

        return new ValidationResult(true, null);
    }
}
