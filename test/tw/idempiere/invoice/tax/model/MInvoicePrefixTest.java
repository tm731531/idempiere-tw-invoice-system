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

import org.junit.Test;

import tw.idempiere.invoice.tax.model.InvoicePrefixValidator.ValidationResult;

/**
 * Unit tests for {@link MInvoicePrefix} validation logic.
 *
 * <p>Because {@link MInvoicePrefix} extends iDempiere's {@code PO} class,
 * instantiating it in a plain JUnit test requires a full OSGi runtime and a
 * registered {@code AD_Table} entry — neither of which is available here.
 * Instead, these tests exercise {@link InvoicePrefixValidator}, the pure-Java
 * class that contains the canonical validation rules and is delegated to by
 * {@code MInvoicePrefix#beforeSave(boolean)}.  This gives full coverage of the
 * business logic without any infrastructure dependency.</p>
 *
 * <p>Tested entry points:
 * <ul>
 *   <li>{@link InvoicePrefixValidator#isValidPrefixCode(String)}</li>
 *   <li>{@link InvoicePrefixValidator#isValidNumberRange(int, int)}</li>
 *   <li>{@link InvoicePrefixValidator#validate(String, int, int, int, boolean)}</li>
 * </ul>
 * </p>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public class MInvoicePrefixTest {

    // =======================================================================
    // isValidPrefixCode() tests
    // =======================================================================

    /**
     * A canonical two-uppercase-letter code (e.g., "AA") must be accepted.
     */
    @Test
    public void testValidPrefixCode() {
        assertTrue("AA should be valid",  InvoicePrefixValidator.isValidPrefixCode("AA"));
        assertTrue("AB should be valid",  InvoicePrefixValidator.isValidPrefixCode("AB"));
        assertTrue("ZZ should be valid",  InvoicePrefixValidator.isValidPrefixCode("ZZ"));
        assertTrue("XY should be valid",  InvoicePrefixValidator.isValidPrefixCode("XY"));
        assertTrue("AZ should be valid",  InvoicePrefixValidator.isValidPrefixCode("AZ"));
    }

    /**
     * Codes with a length other than 2 must be rejected.
     * This covers the empty string, single-char, and three-char cases.
     */
    @Test
    public void testInvalidPrefixCodeLength() {
        assertFalse("null should be invalid",    InvoicePrefixValidator.isValidPrefixCode(null));
        assertFalse("empty string is invalid",   InvoicePrefixValidator.isValidPrefixCode(""));
        assertFalse("single char 'A' invalid",   InvoicePrefixValidator.isValidPrefixCode("A"));
        assertFalse("three chars 'AAA' invalid", InvoicePrefixValidator.isValidPrefixCode("AAA"));
        assertFalse("four chars invalid",        InvoicePrefixValidator.isValidPrefixCode("AAAA"));
    }

    /**
     * Codes containing digits, lowercase letters, or special characters
     * must be rejected, even when the length is 2.
     */
    @Test
    public void testInvalidPrefixCodeFormat() {
        assertFalse("lowercase 'aa' invalid",      InvoicePrefixValidator.isValidPrefixCode("aa"));
        assertFalse("mixed case 'Aa' invalid",     InvoicePrefixValidator.isValidPrefixCode("Aa"));
        assertFalse("digit '1A' invalid",          InvoicePrefixValidator.isValidPrefixCode("1A"));
        assertFalse("digit 'A1' invalid",          InvoicePrefixValidator.isValidPrefixCode("A1"));
        assertFalse("two digits '12' invalid",     InvoicePrefixValidator.isValidPrefixCode("12"));
        assertFalse("special chars '!@' invalid",  InvoicePrefixValidator.isValidPrefixCode("!@"));
        assertFalse("space+letter ' A' invalid",   InvoicePrefixValidator.isValidPrefixCode(" A"));
        assertFalse("letter+space 'A ' invalid",   InvoicePrefixValidator.isValidPrefixCode("A "));
    }

    // =======================================================================
    // isValidNumberRange() tests
    // =======================================================================

    /**
     * A valid range where startNumber equals endNumber must be accepted
     * (single-number range).
     */
    @Test
    public void testValidNumberRangeEqual() {
        assertTrue("start==end should be valid",
            InvoicePrefixValidator.isValidNumberRange(1, 1));
        assertTrue("start==end=100 should be valid",
            InvoicePrefixValidator.isValidNumberRange(100, 100));
    }

    /**
     * A normal range where endNumber is strictly greater than startNumber
     * must be accepted.
     */
    @Test
    public void testValidNumberRangeNormal() {
        assertTrue("1..99999999 valid",
            InvoicePrefixValidator.isValidNumberRange(1, 99999999));
        assertTrue("1000000..8000000 valid",
            InvoicePrefixValidator.isValidNumberRange(1000000, 8000000));
    }

    /**
     * Ranges where endNumber is less than startNumber must be rejected.
     */
    @Test
    public void testInvalidNumberRange() {
        assertFalse("end < start should be invalid",
            InvoicePrefixValidator.isValidNumberRange(100, 99));
        assertFalse("end == 0 while start == 1 invalid",
            InvoicePrefixValidator.isValidNumberRange(1, 0));
        assertFalse("both zero invalid (start must be >= 1)",
            InvoicePrefixValidator.isValidNumberRange(0, 0));
    }

    /**
     * startNumber less than 1 is invalid regardless of endNumber.
     */
    @Test
    public void testInvalidNumberRangeStartBelowOne() {
        assertFalse("start=0 invalid",  InvoicePrefixValidator.isValidNumberRange(0, 100));
        assertFalse("start=-1 invalid", InvoicePrefixValidator.isValidNumberRange(-1, 100));
    }

    // =======================================================================
    // validate() tests — mirrors beforeSave logic
    // =======================================================================

    /**
     * A completely valid set of field values must produce a passing result.
     */
    @Test
    public void testValidateFields_AllValid() {
        ValidationResult result = InvoicePrefixValidator.validate(
            "AA", 1, 99999999, 0, true);
        assertTrue("valid fields should pass", result.valid);
        assertNull("no error message expected", result.errorMessage);
    }

    /**
     * An invalid PrefixCode must cause validation to fail with a
     * descriptive error message.
     */
    @Test
    public void testValidateFields_InvalidPrefixCode() {
        ValidationResult result = InvoicePrefixValidator.validate(
            "aa", 1, 99999999, 0, true);
        assertFalse("lowercase prefix should fail", result.valid);
        assertNotNull("error message must be set", result.errorMessage);
        assertTrue("error message should mention PrefixCode",
            result.errorMessage.contains("PrefixCode"));
    }

    /**
     * A null PrefixCode must cause validation to fail.
     */
    @Test
    public void testValidateFields_NullPrefixCode() {
        ValidationResult result = InvoicePrefixValidator.validate(
            null, 1, 99999999, 0, true);
        assertFalse("null prefix should fail", result.valid);
        assertNotNull("error message must be set", result.errorMessage);
    }

    /**
     * EndNumber less than StartNumber must cause validation to fail.
     */
    @Test
    public void testValidateFields_InvalidNumberRange() {
        ValidationResult result = InvoicePrefixValidator.validate(
            "AA", 100, 50, 0, true);
        assertFalse("end < start should fail", result.valid);
        assertNotNull("error message must be set", result.errorMessage);
        assertTrue("error message should mention EndNumber",
            result.errorMessage.contains("EndNumber"));
    }

    /**
     * StartNumber less than 1 must cause validation to fail.
     */
    @Test
    public void testValidateFields_StartNumberZero() {
        ValidationResult result = InvoicePrefixValidator.validate(
            "AB", 0, 100, 0, true);
        assertFalse("start=0 should fail", result.valid);
        assertNotNull("error message must be set", result.errorMessage);
        assertTrue("error message should mention StartNumber",
            result.errorMessage.contains("StartNumber"));
    }

    /**
     * For a new record where CurrentNumber is 0, the resolved CurrentNumber
     * must be initialised to StartNumber.
     */
    @Test
    public void testInitializeCurrentNumber() {
        int startNumber = 1000000;
        ValidationResult result = InvoicePrefixValidator.validate(
            "AA", startNumber, 8000000, 0, /* newRecord= */ true);
        assertTrue("should pass validation", result.valid);
        assertEquals("CurrentNumber should be initialised to StartNumber",
            startNumber, result.resolvedCurrentNumber);
    }

    /**
     * For an existing (non-new) record where CurrentNumber is 0,
     * the current number must NOT be changed (no auto-initialisation on update).
     */
    @Test
    public void testNoInitializeCurrentNumberOnUpdate() {
        ValidationResult result = InvoicePrefixValidator.validate(
            "AA", 1, 99999999, 0, /* newRecord= */ false);
        assertTrue("should pass validation", result.valid);
        assertEquals("CurrentNumber must remain 0 on update",
            0, result.resolvedCurrentNumber);
    }

    /**
     * For a new record where CurrentNumber is already non-zero,
     * it must remain unchanged (not overwritten by StartNumber).
     */
    @Test
    public void testCurrentNumberNotOverwrittenWhenAlreadySet() {
        ValidationResult result = InvoicePrefixValidator.validate(
            "AA", 1000000, 8000000, 1500000, /* newRecord= */ true);
        assertTrue("should pass validation", result.valid);
        assertEquals("CurrentNumber must not be overwritten when already set",
            1500000, result.resolvedCurrentNumber);
    }

    // =======================================================================
    // Constant / contract tests
    // =======================================================================

    /**
     * Verifies that the public constants defined in {@link MInvoicePrefix}
     * match the expected database values.
     *
     * <p>Note: These tests reference the {@code MInvoicePrefix} class only for
     * its <em>constants</em> (which are inlined by the compiler and do not
     * trigger any iDempiere class initialization at runtime).  The constants
     * themselves — {@code Table_Name}, {@code STATUS_*}, etc. — are plain
     * {@code String} / {@code int} literals and can therefore be read safely
     * in a plain JUnit environment.</p>
     */
    @Test
    public void testConstants() {
        assertEquals("Table_Name",          "TW_InvoicePrefix",  MInvoicePrefix.Table_Name);
        assertEquals("STATUS_Active",       "A",                 MInvoicePrefix.STATUS_Active);
        assertEquals("STATUS_Inactive",     "I",                 MInvoicePrefix.STATUS_Inactive);
        assertEquals("STATUS_Complete",     "C",                 MInvoicePrefix.STATUS_Complete);
        assertEquals("INVOICETYPE_TRIPART", "SALES_TRIPART",     MInvoicePrefix.INVOICETYPE_SALES_TRIPART);
        assertEquals("INVOICETYPE_BIPART",  "SALES_BIPART",      MInvoicePrefix.INVOICETYPE_SALES_BIPART);
    }

    /**
     * Verifies that the {@code COLUMNNAME_*} constants use the exact
     * column name strings expected by the SQL DDL.
     */
    @Test
    public void testColumnNameConstants() {
        assertEquals("PrefixCode",            MInvoicePrefix.COLUMNNAME_PrefixCode);
        assertEquals("InvoiceType",           MInvoicePrefix.COLUMNNAME_InvoiceType);
        assertEquals("CurrentNumber",         MInvoicePrefix.COLUMNNAME_CurrentNumber);
        assertEquals("StartNumber",           MInvoicePrefix.COLUMNNAME_StartNumber);
        assertEquals("EndNumber",             MInvoicePrefix.COLUMNNAME_EndNumber);
        assertEquals("Status",                MInvoicePrefix.COLUMNNAME_Status);
        assertEquals("LastIssuedInvoiceDate", MInvoicePrefix.COLUMNNAME_LastIssuedInvoiceDate);
        assertEquals("LastInvoiceNumber",     MInvoicePrefix.COLUMNNAME_LastInvoiceNumber);
    }
}
