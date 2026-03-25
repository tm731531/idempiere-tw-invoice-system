/******************************************************************************
 * Taiwan Invoice Tax System for iDempiere
 * Copyright (C) Taiwan iDempiere Community. All Rights Reserved.
 * License: GNU General Public License version 2
 *****************************************************************************/
package tw.idempiere.invoice.tax.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.Timestamp;
import java.util.Calendar;

import org.junit.Test;

import tw.idempiere.invoice.tax.model.InvoicePrefixMapValidator.ValidationResult;

/**
 * Unit tests for {@link MInvoicePrefixMap} validation logic.
 *
 * <p>Because {@link MInvoicePrefixMap} extends iDempiere's {@code PO} class,
 * instantiating it in a plain JUnit test requires a full OSGi runtime.
 * Instead, these tests exercise {@link InvoicePrefixMapValidator} directly,
 * which contains all canonical validation rules and is delegated to by
 * {@code MInvoicePrefixMap#beforeSave(boolean)}.</p>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public class MInvoicePrefixMapTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Returns the current time as a {@link Timestamp}. */
    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    /** Returns a timestamp representing a date {@code yearsAgo} years in the past. */
    private static Timestamp yearsAgo(int yearsAgo) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -yearsAgo);
        return new Timestamp(cal.getTimeInMillis());
    }

    // =======================================================================
    // isValidInvoiceNumber() tests
    // =======================================================================

    /**
     * Valid invoice numbers with 7-digit numeric suffix must be accepted.
     */
    @Test
    public void testValidInvoiceNumber_7Digits() {
        assertTrue("AA0000001 valid",  InvoicePrefixMapValidator.isValidInvoiceNumber("AA0000001"));
        assertTrue("ZZ9999999 valid",  InvoicePrefixMapValidator.isValidInvoiceNumber("ZZ9999999"));
        assertTrue("XY1234567 valid",  InvoicePrefixMapValidator.isValidInvoiceNumber("XY1234567"));
    }

    /**
     * Valid invoice numbers with 8-digit numeric suffix must be accepted.
     */
    @Test
    public void testValidInvoiceNumber_8Digits() {
        assertTrue("AA00000001 valid", InvoicePrefixMapValidator.isValidInvoiceNumber("AA00000001"));
        assertTrue("ZZ99999999 valid", InvoicePrefixMapValidator.isValidInvoiceNumber("ZZ99999999"));
    }

    /**
     * Invoice numbers that are null, empty, or have wrong formats must be rejected.
     */
    @Test
    public void testInvalidInvoiceNumber_NullOrEmpty() {
        assertFalse("null invalid",  InvoicePrefixMapValidator.isValidInvoiceNumber(null));
        assertFalse("empty invalid", InvoicePrefixMapValidator.isValidInvoiceNumber(""));
    }

    /**
     * Invoice numbers with lowercase prefix letters must be rejected.
     */
    @Test
    public void testInvalidInvoiceNumber_LowercasePrefix() {
        assertFalse("aa0000001 invalid", InvoicePrefixMapValidator.isValidInvoiceNumber("aa0000001"));
        assertFalse("Aa0000001 invalid", InvoicePrefixMapValidator.isValidInvoiceNumber("Aa0000001"));
    }

    /**
     * Invoice numbers with wrong digit count (< 7 or > 8) must be rejected.
     */
    @Test
    public void testInvalidInvoiceNumber_WrongDigitCount() {
        assertFalse("too few digits AA123456",   InvoicePrefixMapValidator.isValidInvoiceNumber("AA123456"));
        assertFalse("too many digits AA123456789", InvoicePrefixMapValidator.isValidInvoiceNumber("AA123456789"));
    }

    /**
     * Invoice numbers with digit-only prefix must be rejected.
     */
    @Test
    public void testInvalidInvoiceNumber_DigitPrefix() {
        assertFalse("12 prefix invalid", InvoicePrefixMapValidator.isValidInvoiceNumber("120000001"));
    }

    // =======================================================================
    // isExpiryWarning() tests
    // =======================================================================

    /**
     * An invoice older than 9 years and 9 months (within 90 days of 10-year limit)
     * must trigger the expiry warning.
     */
    @Test
    public void testExpiryWarning_NearExpiry() {
        // Invoice issued exactly 9 years and 11 months ago — within 90-day warning window
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -9);
        cal.add(Calendar.MONTH, -11);
        Timestamp nearExpiry = new Timestamp(cal.getTimeInMillis());

        assertTrue("Near-expiry invoice should trigger warning",
            InvoicePrefixMapValidator.isExpiryWarning(nearExpiry, now()));
    }

    /**
     * An invoice only 5 years old must NOT trigger the expiry warning.
     */
    @Test
    public void testExpiryWarning_NotNearExpiry() {
        assertFalse("5-year-old invoice should not trigger warning",
            InvoicePrefixMapValidator.isExpiryWarning(yearsAgo(5), now()));
    }

    /**
     * A null invoice date must not trigger the warning (defensive check).
     */
    @Test
    public void testExpiryWarning_NullDate() {
        assertFalse("null date should not trigger warning",
            InvoicePrefixMapValidator.isExpiryWarning(null, now()));
    }

    // =======================================================================
    // validate() tests
    // =======================================================================

    /**
     * All valid inputs must produce a passing result with IsExpiryWarning='N'.
     */
    @Test
    public void testValidate_AllValid() {
        ValidationResult result = InvoicePrefixMapValidator.validate(
            1,              // prefixId
            100,            // invoiceId
            "AA0000001",    // invoiceNumber
            yearsAgo(1),    // dateInvoiced — 1 year ago, no expiry warning
            now());

        assertTrue("valid inputs should pass", result.valid);
        assertNull("no error message expected", result.errorMessage);
        assertEquals("no expiry warning expected", "N", result.resolvedIsExpiryWarning);
    }

    /**
     * A zero prefixId must cause validation to fail with a descriptive message.
     */
    @Test
    public void testValidate_InvalidPrefixId() {
        ValidationResult result = InvoicePrefixMapValidator.validate(
            0, 100, "AA0000001", yearsAgo(1), now());

        assertFalse("zero prefixId should fail", result.valid);
        assertNotNull("error message must be set", result.errorMessage);
        assertTrue("error message should mention TW_InvoicePrefix_ID",
            result.errorMessage.contains("TW_InvoicePrefix_ID"));
    }

    /**
     * A zero invoiceId must cause validation to fail.
     */
    @Test
    public void testValidate_InvalidInvoiceId() {
        ValidationResult result = InvoicePrefixMapValidator.validate(
            1, 0, "AA0000001", yearsAgo(1), now());

        assertFalse("zero invoiceId should fail", result.valid);
        assertNotNull("error message must be set", result.errorMessage);
        assertTrue("error message should mention C_Invoice_ID",
            result.errorMessage.contains("C_Invoice_ID"));
    }

    /**
     * An invalid invoice number format must cause validation to fail.
     */
    @Test
    public void testValidate_InvalidInvoiceNumber() {
        ValidationResult result = InvoicePrefixMapValidator.validate(
            1, 100, "aa0000001", yearsAgo(1), now());

        assertFalse("invalid invoice number should fail", result.valid);
        assertNotNull("error message must be set", result.errorMessage);
        assertTrue("error message should mention InvoiceNumber",
            result.errorMessage.contains("InvoiceNumber"));
    }

    /**
     * A null DateInvoiced must cause validation to fail.
     */
    @Test
    public void testValidate_NullDateInvoiced() {
        ValidationResult result = InvoicePrefixMapValidator.validate(
            1, 100, "AA0000001", null, now());

        assertFalse("null date should fail", result.valid);
        assertNotNull("error message must be set", result.errorMessage);
        assertTrue("error message should mention DateInvoiced",
            result.errorMessage.contains("DateInvoiced"));
    }

    /**
     * An invoice near the 10-year expiry limit must produce a valid result
     * with IsExpiryWarning='Y'.
     */
    @Test
    public void testValidate_ExpiryWarningSet() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -9);
        cal.add(Calendar.MONTH, -11);
        Timestamp nearExpiry = new Timestamp(cal.getTimeInMillis());

        ValidationResult result = InvoicePrefixMapValidator.validate(
            1, 100, "AA0000001", nearExpiry, now());

        assertTrue("near-expiry invoice should still pass validation", result.valid);
        assertEquals("IsExpiryWarning should be Y", "Y", result.resolvedIsExpiryWarning);
    }

    // =======================================================================
    // Constant / contract tests
    // =======================================================================

    /**
     * Verifies that the public constants defined in {@link MInvoicePrefixMap}
     * match the expected database values.
     */
    @Test
    public void testConstants() {
        assertEquals("Table_Name", "TW_Invoice_Prefix_Map", MInvoicePrefixMap.Table_Name);
    }

    /**
     * Verifies that the {@code COLUMNNAME_*} constants use the exact column
     * name strings expected by the SQL DDL.
     */
    @Test
    public void testColumnNameConstants() {
        assertEquals("TW_InvoicePrefix_ID",    MInvoicePrefixMap.COLUMNNAME_TW_InvoicePrefix_ID);
        assertEquals("C_Invoice_ID",           MInvoicePrefixMap.COLUMNNAME_C_Invoice_ID);
        assertEquals("InvoiceNumber",          MInvoicePrefixMap.COLUMNNAME_InvoiceNumber);
        assertEquals("DateInvoiced",           MInvoicePrefixMap.COLUMNNAME_DateInvoiced);
        assertEquals("IsDateSequenceValid",    MInvoicePrefixMap.COLUMNNAME_IsDateSequenceValid);
        assertEquals("IsMonthConsistent",      MInvoicePrefixMap.COLUMNNAME_IsMonthConsistent);
        assertEquals("MonthConsistentReason",  MInvoicePrefixMap.COLUMNNAME_MonthConsistentReason);
        assertEquals("IsExpiryWarning",        MInvoicePrefixMap.COLUMNNAME_IsExpiryWarning);
    }
}
