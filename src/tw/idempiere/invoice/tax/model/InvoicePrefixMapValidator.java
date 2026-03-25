/******************************************************************************
 * Taiwan Invoice Tax System for iDempiere
 * Copyright (C) Taiwan iDempiere Community. All Rights Reserved.
 * License: GNU General Public License version 2
 *****************************************************************************/
package tw.idempiere.invoice.tax.model;

import java.sql.Timestamp;
import java.util.Calendar;

/**
 * Pure-Java validation logic for {@link MInvoicePrefixMap} fields.
 *
 * <p>This class contains <em>no iDempiere dependencies</em>, which means it
 * can be instantiated and tested in a plain JUnit environment without an OSGi
 * runtime or a database connection.</p>
 *
 * <p>Validation rules:
 * <ol>
 *   <li>{@code TW_InvoicePrefix_ID} must be &gt; 0</li>
 *   <li>{@code C_Invoice_ID} must be &gt; 0</li>
 *   <li>{@code InvoiceNumber} must match the pattern {@code [A-Z]{2}\d{7,8}}</li>
 *   <li>{@code DateInvoiced} must not be {@code null}</li>
 *   <li>{@code IsExpiryWarning} is set to {@code "Y"} when the invoice date
 *       is older than 9 years and 9 months (within 90 days of the 10-year limit)</li>
 * </ol>
 * </p>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public final class InvoicePrefixMapValidator {

    /** Not instantiable — use the static methods directly. */
    private InvoicePrefixMapValidator() {
    }

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /**
     * Number of milliseconds in 90 days, used to compute the expiry warning
     * threshold relative to the 10-year statutory limit.
     */
    static final long NINETY_DAYS_MS = 90L * 24 * 60 * 60 * 1000;

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
         * Resolved value for {@code IsExpiryWarning} after the expiry-threshold
         * check.  Always {@code "Y"} or {@code "N"}.
         */
        public final String resolvedIsExpiryWarning;

        ValidationResult(boolean valid, String errorMessage, String resolvedIsExpiryWarning) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.resolvedIsExpiryWarning = resolvedIsExpiryWarning;
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code invoiceNumber} is a syntactically valid
     * Taiwan invoice number.
     *
     * <p>A valid invoice number consists of exactly 2 uppercase ASCII letters
     * followed by 7 or 8 digits (e.g., {@code AA0000001} or {@code AA00000001}).</p>
     *
     * @param invoiceNumber the number to test (may be {@code null})
     * @return {@code true} if valid
     */
    public static boolean isValidInvoiceNumber(String invoiceNumber) {
        if (invoiceNumber == null) {
            return false;
        }
        return invoiceNumber.matches("[A-Z]{2}\\d{7,8}");
    }

    /**
     * Returns {@code true} if the invoice date is within 90 days of the
     * 10-year statutory expiry for input-tax claims.
     *
     * @param dateInvoiced the invoice date (must not be {@code null})
     * @param now          reference "current" timestamp (use {@code new Timestamp(System.currentTimeMillis())} in production)
     * @return {@code true} when an expiry warning should be raised
     */
    public static boolean isExpiryWarning(Timestamp dateInvoiced, Timestamp now) {
        if (dateInvoiced == null || now == null) {
            return false;
        }
        // 10 years ago from now
        Calendar tenYearsAgo = Calendar.getInstance();
        tenYearsAgo.setTimeInMillis(now.getTime());
        tenYearsAgo.add(Calendar.YEAR, -10);

        long expiryMs = tenYearsAgo.getTimeInMillis();
        long invoicedMs = dateInvoiced.getTime();

        // Expiry warning: invoice is older than (10 years - 90 days)
        return invoicedMs <= (expiryMs + NINETY_DAYS_MS);
    }

    /**
     * Runs the full set of field-level validations that mirror
     * {@code MInvoicePrefixMap#beforeSave(boolean)}.
     *
     * @param prefixId      value of {@code TW_InvoicePrefix_ID} (must be &gt; 0)
     * @param invoiceId     value of {@code C_Invoice_ID} (must be &gt; 0)
     * @param invoiceNumber value of {@code InvoiceNumber}
     * @param dateInvoiced  value of {@code DateInvoiced} (must not be {@code null})
     * @param now           reference timestamp for the expiry-warning calculation
     * @return a {@link ValidationResult} describing the outcome
     */
    public static ValidationResult validate(
            int prefixId,
            int invoiceId,
            String invoiceNumber,
            Timestamp dateInvoiced,
            Timestamp now) {

        // Rule 1: TW_InvoicePrefix_ID
        if (prefixId <= 0) {
            return new ValidationResult(false,
                "TW_InvoicePrefix_ID must be > 0. Got: " + prefixId, "N");
        }

        // Rule 2: C_Invoice_ID
        if (invoiceId <= 0) {
            return new ValidationResult(false,
                "C_Invoice_ID must be > 0. Got: " + invoiceId, "N");
        }

        // Rule 3: InvoiceNumber format
        if (!isValidInvoiceNumber(invoiceNumber)) {
            return new ValidationResult(false,
                "InvoiceNumber must be 2 uppercase letters followed by 7-8 digits. Got: "
                    + invoiceNumber, "N");
        }

        // Rule 4: DateInvoiced must not be null
        if (dateInvoiced == null) {
            return new ValidationResult(false,
                "DateInvoiced must not be null.", "N");
        }

        // Rule 5: Compute expiry warning
        String expiryWarning = isExpiryWarning(dateInvoiced, now) ? "Y" : "N";

        return new ValidationResult(true, null, expiryWarning);
    }
}
