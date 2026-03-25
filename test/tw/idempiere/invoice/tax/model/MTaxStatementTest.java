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

import tw.idempiere.invoice.tax.model.TaxStatementValidator.ValidationResult;

/**
 * Unit tests for {@link MTaxStatement} validation logic.
 *
 * <p>Because {@link MTaxStatement} extends iDempiere's {@code PO} class,
 * these tests exercise {@link TaxStatementValidator} directly, which contains
 * all canonical validation rules and is delegated to by
 * {@code MTaxStatement#beforeSave(boolean)}.</p>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public class MTaxStatementTest {

    // -----------------------------------------------------------------------
    // Helper values
    // -----------------------------------------------------------------------

    private static final BigDecimal BD_1000 = new BigDecimal("1000.00");
    private static final BigDecimal BD_500  = new BigDecimal("500.00");
    private static final BigDecimal BD_50   = new BigDecimal("50.00");
    private static final BigDecimal BD_ZERO = BigDecimal.ZERO;
    private static final BigDecimal BD_NEG  = new BigDecimal("-1.00");

    // =======================================================================
    // isValidStatementPeriod() tests
    // =======================================================================

    /**
     * All six valid bimonthly period values (1–6) must be accepted.
     */
    @Test
    public void testValidStatementPeriods() {
        for (int p = 1; p <= 6; p++) {
            assertTrue("Period " + p + " should be valid",
                TaxStatementValidator.isValidStatementPeriod(p));
        }
    }

    /**
     * Period values outside 1–6 must be rejected.
     */
    @Test
    public void testInvalidStatementPeriods() {
        assertFalse("Period 0 invalid",  TaxStatementValidator.isValidStatementPeriod(0));
        assertFalse("Period 7 invalid",  TaxStatementValidator.isValidStatementPeriod(7));
        assertFalse("Period -1 invalid", TaxStatementValidator.isValidStatementPeriod(-1));
    }

    // =======================================================================
    // isValidStatementYear() tests
    // =======================================================================

    /**
     * Years >= 2000 must be accepted.
     */
    @Test
    public void testValidStatementYears() {
        assertTrue("Year 2000 valid", TaxStatementValidator.isValidStatementYear(2000));
        assertTrue("Year 2026 valid", TaxStatementValidator.isValidStatementYear(2026));
        assertTrue("Year 9999 valid", TaxStatementValidator.isValidStatementYear(9999));
    }

    /**
     * Years before 2000 must be rejected.
     */
    @Test
    public void testInvalidStatementYears() {
        assertFalse("Year 1999 invalid", TaxStatementValidator.isValidStatementYear(1999));
        assertFalse("Year 0 invalid",    TaxStatementValidator.isValidStatementYear(0));
        assertFalse("Year -1 invalid",   TaxStatementValidator.isValidStatementYear(-1));
    }

    // =======================================================================
    // computeEffectiveInputTax() tests
    // =======================================================================

    /**
     * For an ordinary (non-mixed) business, the full input tax must be returned.
     */
    @Test
    public void testComputeEffectiveInputTax_OrdinaryBusiness() {
        BigDecimal effective = TaxStatementValidator.computeEffectiveInputTax(
            BD_1000, false, null);
        assertEquals("ordinary business: full input tax returned",
            BD_1000, effective);
    }

    /**
     * For a mixed business with a 0.6 ratio, input tax must be multiplied by 0.6.
     */
    @Test
    public void testComputeEffectiveInputTax_MixedBusiness() {
        BigDecimal ratio = new BigDecimal("0.6000");
        BigDecimal expected = BD_1000.multiply(ratio); // 600.00
        BigDecimal effective = TaxStatementValidator.computeEffectiveInputTax(
            BD_1000, true, ratio);
        assertEquals("mixed business: input tax apportioned",
            0, expected.compareTo(effective));
    }

    /**
     * Mixed business with a ratio of 1.0 must return the full input tax.
     */
    @Test
    public void testComputeEffectiveInputTax_MixedBusiness_RatioOne() {
        BigDecimal effective = TaxStatementValidator.computeEffectiveInputTax(
            BD_500, true, BigDecimal.ONE);
        assertEquals("ratio 1.0 should return full input tax",
            0, BD_500.compareTo(effective));
    }

    // =======================================================================
    // validate() tests
    // =======================================================================

    /**
     * All valid inputs for an ordinary business must produce a passing result
     * with the correct TaxPayable.
     */
    @Test
    public void testValidate_OrdinaryBusiness_Valid() {
        // Output=1000, Input=50 => TaxPayable=950
        ValidationResult result = TaxStatementValidator.validate(
            1, 2026, BD_1000, BD_ZERO, BD_50, BD_50, false, null);

        assertTrue("valid ordinary business should pass", result.valid);
        assertNull("no error message expected", result.errorMessage);
        assertNotNull("resolvedTaxPayable must not be null", result.resolvedTaxPayable);
        assertEquals("TaxPayable = Output - Input = 0",
            0, BD_ZERO.compareTo(result.resolvedTaxPayable));
    }

    /**
     * All valid inputs for a mixed-business operator must produce a passing
     * result with the apportioned TaxPayable.
     */
    @Test
    public void testValidate_MixedBusiness_Valid() {
        // Output=1000, Input=500, ratio=0.6 => effectiveInput=300, TaxPayable=700
        BigDecimal ratio    = new BigDecimal("0.6000");
        BigDecimal expected = new BigDecimal("700.00"); // 1000 - (500 * 0.6)

        ValidationResult result = TaxStatementValidator.validate(
            1, 2026,
            BD_1000,       // taxableRevenue
            BD_500,        // exemptRevenue
            new BigDecimal("1000.00"), // outputTaxAmount
            BD_500,        // inputTaxAmount
            true,          // isMixedBusiness
            ratio);

        assertTrue("valid mixed business should pass", result.valid);
        assertNull("no error message expected", result.errorMessage);
        assertEquals("TaxPayable apportioned correctly",
            0, expected.compareTo(result.resolvedTaxPayable));
    }

    /**
     * An invalid statement period must cause validation to fail.
     */
    @Test
    public void testValidate_InvalidPeriod() {
        ValidationResult result = TaxStatementValidator.validate(
            7, 2026, BD_1000, BD_ZERO, BD_50, BD_50, false, null);

        assertFalse("period 7 should fail", result.valid);
        assertNotNull("error message must be set", result.errorMessage);
        assertTrue("error should mention StatementPeriod",
            result.errorMessage.contains("StatementPeriod"));
    }

    /**
     * A year before 2000 must cause validation to fail.
     */
    @Test
    public void testValidate_InvalidYear() {
        ValidationResult result = TaxStatementValidator.validate(
            1, 1999, BD_1000, BD_ZERO, BD_50, BD_50, false, null);

        assertFalse("year 1999 should fail", result.valid);
        assertNotNull("error message must be set", result.errorMessage);
        assertTrue("error should mention StatementYear",
            result.errorMessage.contains("StatementYear"));
    }

    /**
     * A negative TaxableRevenue must cause validation to fail.
     */
    @Test
    public void testValidate_NegativeTaxableRevenue() {
        ValidationResult result = TaxStatementValidator.validate(
            1, 2026, BD_NEG, BD_ZERO, BD_50, BD_50, false, null);

        assertFalse("negative TaxableRevenue should fail", result.valid);
        assertTrue("error should mention TaxableRevenue",
            result.errorMessage.contains("TaxableRevenue"));
    }

    /**
     * A negative ExemptRevenue must cause validation to fail.
     */
    @Test
    public void testValidate_NegativeExemptRevenue() {
        ValidationResult result = TaxStatementValidator.validate(
            1, 2026, BD_1000, BD_NEG, BD_50, BD_50, false, null);

        assertFalse("negative ExemptRevenue should fail", result.valid);
        assertTrue("error should mention ExemptRevenue",
            result.errorMessage.contains("ExemptRevenue"));
    }

    /**
     * Mixed-business operator with a null ratio must cause validation to fail.
     */
    @Test
    public void testValidate_MixedBusiness_NullRatio() {
        ValidationResult result = TaxStatementValidator.validate(
            1, 2026, BD_1000, BD_500, BD_50, BD_50, true, null);

        assertFalse("mixed business with null ratio should fail", result.valid);
        assertTrue("error should mention MixedBusinessRatio",
            result.errorMessage.contains("MixedBusinessRatio"));
    }

    /**
     * Mixed-business ratio greater than 1 must cause validation to fail.
     */
    @Test
    public void testValidate_MixedBusiness_RatioGreaterThanOne() {
        BigDecimal badRatio = new BigDecimal("1.0001");
        ValidationResult result = TaxStatementValidator.validate(
            1, 2026, BD_1000, BD_500, BD_50, BD_50, true, badRatio);

        assertFalse("ratio > 1 should fail", result.valid);
        assertTrue("error should mention MixedBusinessRatio",
            result.errorMessage.contains("MixedBusinessRatio"));
    }

    /**
     * A non-mixed business with a non-null MixedBusinessRatio must fail.
     */
    @Test
    public void testValidate_OrdinaryBusiness_RatioProvided() {
        ValidationResult result = TaxStatementValidator.validate(
            1, 2026, BD_1000, BD_ZERO, BD_50, BD_50, false,
            new BigDecimal("0.8000"));

        assertFalse("non-mixed business with ratio should fail", result.valid);
        assertTrue("error should mention MixedBusinessRatio",
            result.errorMessage.contains("MixedBusinessRatio"));
    }

    /**
     * A refund scenario (output tax less than input tax) must produce a
     * negative TaxPayable without failing validation.
     */
    @Test
    public void testValidate_NegativeTaxPayable_Refund() {
        // Output=50, Input=500 => TaxPayable=-450 (credit refund)
        BigDecimal output = BD_50;
        BigDecimal input  = BD_500;
        ValidationResult result = TaxStatementValidator.validate(
            1, 2026, BD_1000, BD_ZERO, output, input, false, null);

        assertTrue("refund scenario should still pass validation", result.valid);
        assertTrue("TaxPayable should be negative for refund",
            result.resolvedTaxPayable.compareTo(BigDecimal.ZERO) < 0);
    }

    // =======================================================================
    // Constant / contract tests
    // =======================================================================

    /**
     * Verifies that the public constants match the expected database values.
     */
    @Test
    public void testConstants() {
        assertEquals("Table_Name", "TW_TaxStatement", MTaxStatement.Table_Name);
    }

    /**
     * Verifies that the {@code COLUMNNAME_*} constants use the exact column
     * name strings expected by the SQL DDL.
     */
    @Test
    public void testColumnNameConstants() {
        assertEquals("StatementPeriod",    MTaxStatement.COLUMNNAME_StatementPeriod);
        assertEquals("StatementYear",      MTaxStatement.COLUMNNAME_StatementYear);
        assertEquals("TaxableRevenue",     MTaxStatement.COLUMNNAME_TaxableRevenue);
        assertEquals("ExemptRevenue",      MTaxStatement.COLUMNNAME_ExemptRevenue);
        assertEquals("OutputTaxAmount",    MTaxStatement.COLUMNNAME_OutputTaxAmount);
        assertEquals("InputTaxAmount",     MTaxStatement.COLUMNNAME_InputTaxAmount);
        assertEquals("TaxPayable",         MTaxStatement.COLUMNNAME_TaxPayable);
        assertEquals("IsMixedBusiness",    MTaxStatement.COLUMNNAME_IsMixedBusiness);
        assertEquals("MixedBusinessRatio", MTaxStatement.COLUMNNAME_MixedBusinessRatio);
    }
}
