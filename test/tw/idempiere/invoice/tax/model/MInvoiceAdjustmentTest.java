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

import java.math.BigDecimal;

import org.junit.Test;

import tw.idempiere.invoice.tax.model.InvoiceAdjustmentValidator.ValidationResult;

/**
 * Unit tests for {@link MInvoiceAdjustment} validation logic.
 *
 * <p>Because {@link MInvoiceAdjustment} extends iDempiere's {@code PO} class,
 * these tests exercise {@link InvoiceAdjustmentValidator} directly, which
 * contains all canonical validation rules.</p>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public class MInvoiceAdjustmentTest {

    // -----------------------------------------------------------------------
    // Helper values
    // -----------------------------------------------------------------------

    private static final BigDecimal AMOUNT_1000  = new BigDecimal("1000.00");
    private static final BigDecimal AMOUNT_50    = new BigDecimal("50.00");
    private static final BigDecimal AMOUNT_NEG   = new BigDecimal("-1.00");
    private static final BigDecimal AMOUNT_ZERO  = BigDecimal.ZERO;

    // =======================================================================
    // isValidAdjustmentType() tests
    // =======================================================================

    /**
     * Both recognised adjustment types must be accepted.
     */
    @Test
    public void testValidAdjustmentType() {
        assertTrue("RETURN valid",    InvoiceAdjustmentValidator.isValidAdjustmentType("RETURN"));
        assertTrue("ALLOWANCE valid", InvoiceAdjustmentValidator.isValidAdjustmentType("ALLOWANCE"));
    }

    /**
     * Null, empty, or unrecognised adjustment types must be rejected.
     */
    @Test
    public void testInvalidAdjustmentType() {
        assertFalse("null invalid",      InvoiceAdjustmentValidator.isValidAdjustmentType(null));
        assertFalse("empty invalid",     InvoiceAdjustmentValidator.isValidAdjustmentType(""));
        assertFalse("DEBIT invalid",     InvoiceAdjustmentValidator.isValidAdjustmentType("DEBIT"));
        assertFalse("return lower",      InvoiceAdjustmentValidator.isValidAdjustmentType("return"));
        assertFalse("allowance lower",   InvoiceAdjustmentValidator.isValidAdjustmentType("allowance"));
    }

    // =======================================================================
    // isValidReportingPeriod() tests
    // =======================================================================

    /**
     * Correctly formatted bimonthly period identifiers must be accepted.
     */
    @Test
    public void testValidReportingPeriod() {
        assertTrue("202601 valid", InvoiceAdjustmentValidator.isValidReportingPeriod("202601"));
        assertTrue("202603 valid", InvoiceAdjustmentValidator.isValidReportingPeriod("202603"));
        assertTrue("202605 valid", InvoiceAdjustmentValidator.isValidReportingPeriod("202605"));
        assertTrue("202607 valid", InvoiceAdjustmentValidator.isValidReportingPeriod("202607"));
        assertTrue("202609 valid", InvoiceAdjustmentValidator.isValidReportingPeriod("202609"));
        assertTrue("202611 valid", InvoiceAdjustmentValidator.isValidReportingPeriod("202611"));
    }

    /**
     * Even months (non-first-of-bimonth) must be rejected.
     */
    @Test
    public void testInvalidReportingPeriod_EvenMonths() {
        assertFalse("202602 invalid", InvoiceAdjustmentValidator.isValidReportingPeriod("202602"));
        assertFalse("202604 invalid", InvoiceAdjustmentValidator.isValidReportingPeriod("202604"));
        assertFalse("202606 invalid", InvoiceAdjustmentValidator.isValidReportingPeriod("202606"));
        assertFalse("202608 invalid", InvoiceAdjustmentValidator.isValidReportingPeriod("202608"));
        assertFalse("202610 invalid", InvoiceAdjustmentValidator.isValidReportingPeriod("202610"));
        assertFalse("202612 invalid", InvoiceAdjustmentValidator.isValidReportingPeriod("202612"));
    }

    /**
     * Null, empty, wrong-length, and non-numeric values must be rejected.
     */
    @Test
    public void testInvalidReportingPeriod_BadFormat() {
        assertFalse("null invalid",         InvoiceAdjustmentValidator.isValidReportingPeriod(null));
        assertFalse("empty invalid",        InvoiceAdjustmentValidator.isValidReportingPeriod(""));
        assertFalse("too short invalid",    InvoiceAdjustmentValidator.isValidReportingPeriod("20260"));
        assertFalse("too long invalid",     InvoiceAdjustmentValidator.isValidReportingPeriod("2026011"));
        assertFalse("non-numeric invalid",  InvoiceAdjustmentValidator.isValidReportingPeriod("2026AB"));
    }

    /**
     * Years before 2000 must be rejected.
     */
    @Test
    public void testInvalidReportingPeriod_OldYear() {
        assertFalse("year 1999 invalid", InvoiceAdjustmentValidator.isValidReportingPeriod("199901"));
    }

    // =======================================================================
    // validate() tests
    // =======================================================================

    /**
     * All valid inputs (RETURN type) must produce a passing result.
     */
    @Test
    public void testValidate_AllValid_Return() {
        ValidationResult result = InvoiceAdjustmentValidator.validate(
            1,              // prefixMapId
            "RETURN",       // adjustmentType
            AMOUNT_1000,    // adjustmentAmount
            AMOUNT_50,      // inputTaxAmount
            "202601");      // requiredReportingPeriod

        assertTrue("valid RETURN should pass", result.valid);
        assertNull("no error message expected", result.errorMessage);
    }

    /**
     * All valid inputs (ALLOWANCE type) must produce a passing result.
     */
    @Test
    public void testValidate_AllValid_Allowance() {
        ValidationResult result = InvoiceAdjustmentValidator.validate(
            2, "ALLOWANCE", AMOUNT_1000, AMOUNT_ZERO, "202611");

        assertTrue("valid ALLOWANCE should pass", result.valid);
        assertNull("no error message expected", result.errorMessage);
    }

    /**
     * InputTaxAmount equal to AdjustmentAmount (edge case) must be accepted.
     */
    @Test
    public void testValidate_InputTaxEqualsAdjustment() {
        ValidationResult result = InvoiceAdjustmentValidator.validate(
            1, "RETURN", AMOUNT_1000, AMOUNT_1000, "202601");

        assertTrue("InputTax == AdjustmentAmount should pass", result.valid);
    }

    /**
     * A zero prefixMapId must cause validation to fail.
     */
    @Test
    public void testValidate_ZeroPrefixMapId() {
        ValidationResult result = InvoiceAdjustmentValidator.validate(
            0, "RETURN", AMOUNT_1000, AMOUNT_50, "202601");

        assertFalse("zero prefixMapId should fail", result.valid);
        assertNotNull("error message must be set", result.errorMessage);
        assertTrue("error should mention TW_InvoicePrefixMap_ID",
            result.errorMessage.contains("TW_InvoicePrefixMap_ID"));
    }

    /**
     * An unrecognised AdjustmentType must cause validation to fail.
     */
    @Test
    public void testValidate_InvalidAdjustmentType() {
        ValidationResult result = InvoiceAdjustmentValidator.validate(
            1, "DEBIT", AMOUNT_1000, AMOUNT_50, "202601");

        assertFalse("invalid type should fail", result.valid);
        assertNotNull("error message must be set", result.errorMessage);
        assertTrue("error should mention AdjustmentType",
            result.errorMessage.contains("AdjustmentType"));
    }

    /**
     * A zero or negative AdjustmentAmount must cause validation to fail.
     */
    @Test
    public void testValidate_InvalidAdjustmentAmount() {
        ValidationResult zeroResult = InvoiceAdjustmentValidator.validate(
            1, "RETURN", AMOUNT_ZERO, AMOUNT_ZERO, "202601");
        assertFalse("zero amount should fail", zeroResult.valid);

        ValidationResult negResult = InvoiceAdjustmentValidator.validate(
            1, "RETURN", AMOUNT_NEG, AMOUNT_ZERO, "202601");
        assertFalse("negative amount should fail", negResult.valid);
        assertTrue("error should mention AdjustmentAmount",
            negResult.errorMessage.contains("AdjustmentAmount"));
    }

    /**
     * InputTaxAmount greater than AdjustmentAmount must cause validation to fail.
     */
    @Test
    public void testValidate_InputTaxExceedsAdjustment() {
        BigDecimal largeInputTax = new BigDecimal("2000.00");
        ValidationResult result = InvoiceAdjustmentValidator.validate(
            1, "RETURN", AMOUNT_1000, largeInputTax, "202601");

        assertFalse("InputTax > AdjustmentAmount should fail", result.valid);
        assertNotNull("error message must be set", result.errorMessage);
        assertTrue("error should mention InputTaxAmount",
            result.errorMessage.contains("InputTaxAmount"));
    }

    /**
     * A negative InputTaxAmount must cause validation to fail.
     */
    @Test
    public void testValidate_NegativeInputTax() {
        ValidationResult result = InvoiceAdjustmentValidator.validate(
            1, "RETURN", AMOUNT_1000, AMOUNT_NEG, "202601");

        assertFalse("negative InputTax should fail", result.valid);
        assertTrue("error should mention InputTaxAmount",
            result.errorMessage.contains("InputTaxAmount"));
    }

    /**
     * An invalid reporting period must cause validation to fail.
     */
    @Test
    public void testValidate_InvalidReportingPeriod() {
        ValidationResult result = InvoiceAdjustmentValidator.validate(
            1, "RETURN", AMOUNT_1000, AMOUNT_50, "202602");

        assertFalse("even month period should fail", result.valid);
        assertNotNull("error message must be set", result.errorMessage);
        assertTrue("error should mention RequiredReportingPeriod",
            result.errorMessage.contains("RequiredReportingPeriod"));
    }

    // =======================================================================
    // Constant / contract tests
    // =======================================================================

    /**
     * Verifies that the public constants match the expected database values.
     */
    @Test
    public void testConstants() {
        assertEquals("Table_Name",           "TW_InvoiceAdjustment", MInvoiceAdjustment.Table_Name);
        assertEquals("ADJUSTMENTTYPE_RETURN",    "RETURN",    MInvoiceAdjustment.ADJUSTMENTTYPE_RETURN);
        assertEquals("ADJUSTMENTTYPE_ALLOWANCE", "ALLOWANCE", MInvoiceAdjustment.ADJUSTMENTTYPE_ALLOWANCE);
    }

    /**
     * Verifies that the {@code COLUMNNAME_*} constants use the exact column
     * name strings expected by the SQL DDL.
     */
    @Test
    public void testColumnNameConstants() {
        assertEquals("TW_InvoicePrefixMap_ID",    MInvoiceAdjustment.COLUMNNAME_TW_InvoicePrefixMap_ID);
        assertEquals("AdjustmentType",            MInvoiceAdjustment.COLUMNNAME_AdjustmentType);
        assertEquals("AdjustmentAmount",          MInvoiceAdjustment.COLUMNNAME_AdjustmentAmount);
        assertEquals("InputTaxAmount",            MInvoiceAdjustment.COLUMNNAME_InputTaxAmount);
        assertEquals("RequiredReportingPeriod",   MInvoiceAdjustment.COLUMNNAME_RequiredReportingPeriod);
        assertEquals("IsReportedOnTime",          MInvoiceAdjustment.COLUMNNAME_IsReportedOnTime);
        assertEquals("OverdueWarning",            MInvoiceAdjustment.COLUMNNAME_OverdueWarning);
    }
}
