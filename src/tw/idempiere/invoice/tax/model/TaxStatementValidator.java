/******************************************************************************
 * Taiwan Invoice Tax System for iDempiere
 * Copyright (C) Taiwan iDempiere Community. All Rights Reserved.
 * License: GNU General Public License version 2
 *****************************************************************************/
package tw.idempiere.invoice.tax.model;

import java.math.BigDecimal;

/**
 * Pure-Java validation logic for {@link MTaxStatement} fields.
 *
 * <p>This class contains <em>no iDempiere dependencies</em>, which means it
 * can be instantiated and tested in a plain JUnit environment without an OSGi
 * runtime or a database connection.</p>
 *
 * <p>Validation rules:
 * <ol>
 *   <li>{@code StatementPeriod} must be between 1 and 6 (inclusive)</li>
 *   <li>{@code StatementYear} must be &gt;= 2000</li>
 *   <li>{@code TaxableRevenue} must be &gt;= 0</li>
 *   <li>{@code ExemptRevenue} must be &gt;= 0</li>
 *   <li>{@code OutputTaxAmount} must be &gt;= 0</li>
 *   <li>{@code InputTaxAmount} must be &gt;= 0</li>
 *   <li>When {@code isMixedBusiness == true}, {@code mixedBusinessRatio} must
 *       be between 0.0000 and 1.0000 (inclusive)</li>
 *   <li>When {@code isMixedBusiness == false}, {@code mixedBusinessRatio} must
 *       be {@code null}</li>
 * </ol>
 * </p>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public final class TaxStatementValidator {

    /** Not instantiable — use the static methods directly. */
    private TaxStatementValidator() {
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
         * Resolved {@code TaxPayable} value after applying the mixed-business
         * apportionment ratio (if applicable).
         *
         * <p>Formula:
         * <ul>
         *   <li>Ordinary business: {@code outputTax - inputTax}</li>
         *   <li>Mixed business: {@code outputTax - (inputTax * ratio)}</li>
         * </ul>
         * </p>
         */
        public final BigDecimal resolvedTaxPayable;

        ValidationResult(boolean valid, String errorMessage, BigDecimal resolvedTaxPayable) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.resolvedTaxPayable = resolvedTaxPayable;
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code period} is a valid bimonthly statement
     * period (1–6 inclusive).
     *
     * @param period the period value to test
     * @return {@code true} if valid
     */
    public static boolean isValidStatementPeriod(int period) {
        return period >= 1 && period <= 6;
    }

    /**
     * Returns {@code true} if {@code year} is a plausible statement year
     * (&gt;= 2000).
     *
     * @param year the year to test
     * @return {@code true} if valid
     */
    public static boolean isValidStatementYear(int year) {
        return year >= 2000;
    }

    /**
     * Computes the effective input tax after mixed-business apportionment.
     *
     * <p>For ordinary businesses (not mixed), the full {@code inputTaxAmount}
     * is deductible.  For mixed businesses the deductible amount is
     * {@code inputTaxAmount * mixedBusinessRatio}.</p>
     *
     * @param inputTaxAmount     total input tax before apportionment (must not be {@code null})
     * @param isMixedBusiness    {@code true} for 兼營營業人
     * @param mixedBusinessRatio apportionment ratio (0–1); ignored when {@code isMixedBusiness == false}
     * @return effective (deductible) input tax amount
     */
    public static BigDecimal computeEffectiveInputTax(
            BigDecimal inputTaxAmount,
            boolean isMixedBusiness,
            BigDecimal mixedBusinessRatio) {

        if (!isMixedBusiness || mixedBusinessRatio == null) {
            return inputTaxAmount;
        }
        return inputTaxAmount.multiply(mixedBusinessRatio);
    }

    /**
     * Runs the full set of field-level validations that mirror
     * {@code MTaxStatement#beforeSave(boolean)}.
     *
     * @param statementPeriod    value of {@code StatementPeriod} (1–6)
     * @param statementYear      value of {@code StatementYear} (&gt;= 2000)
     * @param taxableRevenue     value of {@code TaxableRevenue} (&gt;= 0)
     * @param exemptRevenue      value of {@code ExemptRevenue} (&gt;= 0)
     * @param outputTaxAmount    value of {@code OutputTaxAmount} (&gt;= 0)
     * @param inputTaxAmount     value of {@code InputTaxAmount} (&gt;= 0)
     * @param isMixedBusiness    value of {@code IsMixedBusiness}
     * @param mixedBusinessRatio value of {@code MixedBusinessRatio} (0–1, or {@code null})
     * @return a {@link ValidationResult} describing the outcome
     */
    public static ValidationResult validate(
            int statementPeriod,
            int statementYear,
            BigDecimal taxableRevenue,
            BigDecimal exemptRevenue,
            BigDecimal outputTaxAmount,
            BigDecimal inputTaxAmount,
            boolean isMixedBusiness,
            BigDecimal mixedBusinessRatio) {

        // Rule 1: StatementPeriod
        if (!isValidStatementPeriod(statementPeriod)) {
            return new ValidationResult(false,
                "StatementPeriod must be between 1 and 6. Got: " + statementPeriod, null);
        }

        // Rule 2: StatementYear
        if (!isValidStatementYear(statementYear)) {
            return new ValidationResult(false,
                "StatementYear must be >= 2000. Got: " + statementYear, null);
        }

        // Rule 3: TaxableRevenue >= 0
        if (taxableRevenue == null || taxableRevenue.compareTo(BigDecimal.ZERO) < 0) {
            return new ValidationResult(false,
                "TaxableRevenue must be >= 0. Got: " + taxableRevenue, null);
        }

        // Rule 4: ExemptRevenue >= 0
        if (exemptRevenue == null || exemptRevenue.compareTo(BigDecimal.ZERO) < 0) {
            return new ValidationResult(false,
                "ExemptRevenue must be >= 0. Got: " + exemptRevenue, null);
        }

        // Rule 5: OutputTaxAmount >= 0
        if (outputTaxAmount == null || outputTaxAmount.compareTo(BigDecimal.ZERO) < 0) {
            return new ValidationResult(false,
                "OutputTaxAmount must be >= 0. Got: " + outputTaxAmount, null);
        }

        // Rule 6: InputTaxAmount >= 0
        if (inputTaxAmount == null || inputTaxAmount.compareTo(BigDecimal.ZERO) < 0) {
            return new ValidationResult(false,
                "InputTaxAmount must be >= 0. Got: " + inputTaxAmount, null);
        }

        // Rule 7 & 8: Mixed-business ratio consistency
        if (isMixedBusiness) {
            if (mixedBusinessRatio == null) {
                return new ValidationResult(false,
                    "MixedBusinessRatio must not be null for a mixed-business operator.", null);
            }
            if (mixedBusinessRatio.compareTo(BigDecimal.ZERO) < 0
                    || mixedBusinessRatio.compareTo(BigDecimal.ONE) > 0) {
                return new ValidationResult(false,
                    "MixedBusinessRatio must be between 0 and 1. Got: " + mixedBusinessRatio,
                    null);
            }
        } else {
            if (mixedBusinessRatio != null) {
                return new ValidationResult(false,
                    "MixedBusinessRatio must be null for an ordinary (non-mixed) business.", null);
            }
        }

        // Compute resolved TaxPayable
        BigDecimal effectiveInput = computeEffectiveInputTax(
            inputTaxAmount, isMixedBusiness, mixedBusinessRatio);
        BigDecimal taxPayable = outputTaxAmount.subtract(effectiveInput);

        return new ValidationResult(true, null, taxPayable);
    }
}
