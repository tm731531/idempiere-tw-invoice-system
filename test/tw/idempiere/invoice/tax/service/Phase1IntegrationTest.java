/******************************************************************************
 * Taiwan Invoice Tax System for iDempiere
 * Copyright (C) Taiwan iDempiere Community. All Rights Reserved.
 * License: GNU General Public License version 2
 *****************************************************************************/
package tw.idempiere.invoice.tax.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import tw.idempiere.invoice.tax.model.InvoiceAdjustmentValidator;
import tw.idempiere.invoice.tax.model.InvoicePrefixMapValidator;
import tw.idempiere.invoice.tax.model.InvoicePrefixValidator;
import tw.idempiere.invoice.tax.model.MInvoiceAdjustment;
import tw.idempiere.invoice.tax.model.MInvoicePrefix;
import tw.idempiere.invoice.tax.model.MInvoicePrefixMap;
import tw.idempiere.invoice.tax.model.MTaxStatement;
import tw.idempiere.invoice.tax.model.TaxStatementValidator;
import tw.idempiere.invoice.tax.service.AccountingCodeMapper;
import tw.idempiere.invoice.tax.service.AccountingCodeMapper.AccountInfo;
import tw.idempiere.invoice.tax.service.AccountingCodeMapperValidator;
import tw.idempiere.invoice.tax.util.AccountingCodes;

/**
 * Phase 1 Integration Tests for the Taiwan Invoice Tax System.
 *
 * <p>These tests verify that all Phase 1 components — Models, Validators, and
 * the AccountingCodeMapper — work together correctly end-to-end.  No iDempiere
 * OSGi runtime or live database is required: the Mapper's DB layer is replaced
 * with an in-memory stub, and the Model validators are pure-Java classes.</p>
 *
 * <h3>Scenarios covered</h3>
 * <ol>
 *   <li>Invoice prefix creation and validation (testCreateInvoicePrefixAndMap)</li>
 *   <li>Adjustment with correct tax period (testAdjustmentWithCorrectTaxPeriod)</li>
 *   <li>Mixed-business apportionment ratio (testMixedBusinessAdjustment)</li>
 *   <li>Accounting code dynamic mapping (testAccountingCodeMapping)</li>
 *   <li>6-period tax statement calculation (testTaxStatementCalculation)</li>
 *   <li>10-year input-tax expiry warning (testInputTaxExpiry)</li>
 *   <li>All validators cooperating (testValidationChain)</li>
 *   <li>Tenant isolation for different AD_Org_ID / AD_Client_ID
 *       (testTenantIsolation)</li>
 * </ol>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public class Phase1IntegrationTest {

    // -----------------------------------------------------------------------
    // Shared context / fixture constants
    // -----------------------------------------------------------------------

    /** Simulated AD_Client_ID for Organisation A (e.g. Company TW-001). */
    private static final int ORG_A_CLIENT_ID = 1001;

    /** Simulated AD_Client_ID for Organisation B (e.g. Company TW-002). */
    private static final int ORG_B_CLIENT_ID = 2002;

    /** Properties context — empty is fine; tests do not call iDempiere PO save. */
    private static final Properties CTX = new Properties();

    /** Null transaction name — acceptable for in-memory tests. */
    private static final String TRX = null;

    // -----------------------------------------------------------------------
    // Testable subclass — replaces DB query with in-memory stub
    // -----------------------------------------------------------------------

    /**
     * Test double for {@link AccountingCodeMapper}: overrides
     * {@code loadAccountsFromDB} to return pre-registered in-memory data,
     * keeping tests free of any iDempiere runtime dependency.
     */
    private static class StubAccountingCodeMapper extends AccountingCodeMapper {

        private final Map<Integer, Map<String, AccountInfo>> stubData = new HashMap<>();

        void registerAccounts(int clientId, Map<String, AccountInfo> accounts) {
            stubData.put(clientId, Collections.unmodifiableMap(new HashMap<>(accounts)));
        }

        @Override
        protected Map<String, AccountInfo> loadAccountsFromDB(int clientId, String trxName) {
            Map<String, AccountInfo> data = stubData.get(clientId);
            return (data != null) ? data : Collections.emptyMap();
        }
    }

    // -----------------------------------------------------------------------
    // Shared fixture
    // -----------------------------------------------------------------------

    private StubAccountingCodeMapper mapper;
    private AccountingCodeMapperValidator mapperValidator;

    /** Registers the full standard Taiwan chart-of-accounts for one client. */
    private void registerFullChartOfAccounts(int clientId) {
        Map<String, AccountInfo> accounts = new HashMap<>();
        accounts.put("1112", new AccountInfo("1112", "應收進項稅額", "A", clientId * 10 + 1));
        accounts.put("2121", new AccountInfo("2121", "應付營業稅",   "L", clientId * 10 + 2));
        accounts.put("4100", new AccountInfo("4100", "銷售收入-應稅", "R", clientId * 10 + 3));
        accounts.put("4200", new AccountInfo("4200", "銷售收入-免稅", "R", clientId * 10 + 4));
        accounts.put("5100", new AccountInfo("5100", "銷貨成本",      "E", clientId * 10 + 5));
        mapper.registerAccounts(clientId, accounts);
    }

    @Before
    public void setUp() {
        mapper = new StubAccountingCodeMapper();
        registerFullChartOfAccounts(ORG_A_CLIENT_ID);
        registerFullChartOfAccounts(ORG_B_CLIENT_ID);
        mapperValidator = new AccountingCodeMapperValidator(mapper);
    }

    // -----------------------------------------------------------------------
    // 1. testCreateInvoicePrefixAndMap
    //    Verifies: MInvoicePrefix initialisation + InvoicePrefixValidator
    //              MInvoicePrefixMap initialisation + InvoicePrefixMapValidator
    //              Prefix-to-Map association
    // -----------------------------------------------------------------------

    /**
     * A freshly-created {@link MInvoicePrefix} should have sensible defaults
     * and pass field validation; an {@link MInvoicePrefixMap} can then be
     * linked to that prefix.
     */
    @Test
    public void testCreateInvoicePrefixAndMap() {
        // --- Create Prefix ---
        // Simulate: new MInvoicePrefix without calling iDempiere PO save
        // Validate using the pure-Java validator directly
        InvoicePrefixValidator.ValidationResult prefixResult =
            InvoicePrefixValidator.validate("AA", 1, 99999999, 0, true);

        assertTrue("Prefix 'AA' with default range must be valid", prefixResult.valid);
        assertNull("No error message on success", prefixResult.errorMessage);
        assertEquals("CurrentNumber should be initialised to StartNumber on new record",
            1, prefixResult.resolvedCurrentNumber);

        // Verify constant values match DDL expectations
        assertEquals("Table_Name must match DDL", "TW_InvoicePrefix", MInvoicePrefix.Table_Name);
        assertEquals("STATUS_Active constant", "A", MInvoicePrefix.STATUS_Active);
        assertEquals("STATUS_Inactive constant", "I", MInvoicePrefix.STATUS_Inactive);
        assertEquals("STATUS_Complete constant", "C", MInvoicePrefix.STATUS_Complete);
        assertEquals("INVOICETYPE_SALES_TRIPART constant",
            "SALES_TRIPART", MInvoicePrefix.INVOICETYPE_SALES_TRIPART);
        assertEquals("INVOICETYPE_SALES_BIPART constant",
            "SALES_BIPART", MInvoicePrefix.INVOICETYPE_SALES_BIPART);

        // --- Create Prefix Map ---
        // Simulate mapping invoice TW_InvoicePrefix_ID=5, C_Invoice_ID=100,
        // InvoiceNumber=AA0000001 issued today
        Timestamp now = new Timestamp(System.currentTimeMillis());
        InvoicePrefixMapValidator.ValidationResult mapResult =
            InvoicePrefixMapValidator.validate(5, 100, "AA0000001", now, now);

        assertTrue("Map with valid data must pass validation", mapResult.valid);
        assertNull("No error message on success", mapResult.errorMessage);
        assertEquals("Invoice just issued should not trigger expiry warning",
            "N", mapResult.resolvedIsExpiryWarning);

        // Verify Table_Name constant
        assertEquals("TW_Invoice_Prefix_Map", MInvoicePrefixMap.Table_Name);
    }

    // -----------------------------------------------------------------------
    // 2. testAdjustmentWithCorrectTaxPeriod
    //    Verifies: MInvoiceAdjustment + InvoiceAdjustmentValidator
    //              Bimonthly period validation for an on-time adjustment
    // -----------------------------------------------------------------------

    /**
     * An invoice adjustment (折讓) filed in the correct bimonthly window must
     * pass all validations.  The test covers both RETURN and ALLOWANCE types.
     */
    @Test
    public void testAdjustmentWithCorrectTaxPeriod() {
        BigDecimal amount  = new BigDecimal("10000.00");
        BigDecimal taxPart = new BigDecimal("500.00");  // 5% of 10000

        // ALLOWANCE adjustment in period 202601 (Jan–Feb 2026)
        InvoiceAdjustmentValidator.ValidationResult allowanceResult =
            InvoiceAdjustmentValidator.validate(1, "ALLOWANCE", amount, taxPart, "202601");

        assertTrue("Valid ALLOWANCE must pass", allowanceResult.valid);
        assertNull("No error on valid ALLOWANCE", allowanceResult.errorMessage);

        // RETURN adjustment in period 202603 (Mar–Apr 2026)
        InvoiceAdjustmentValidator.ValidationResult returnResult =
            InvoiceAdjustmentValidator.validate(2, "RETURN", amount, taxPart, "202603");

        assertTrue("Valid RETURN must pass", returnResult.valid);
        assertNull("No error on valid RETURN", returnResult.errorMessage);

        // Verify all six bimonthly windows are accepted
        String[] validPeriods = {"202601","202603","202605","202607","202609","202611"};
        for (String period : validPeriods) {
            InvoiceAdjustmentValidator.ValidationResult r =
                InvoiceAdjustmentValidator.validate(1, "ALLOWANCE", amount, taxPart, period);
            assertTrue("Period " + period + " must be valid", r.valid);
        }

        // Even months (e.g. Feb, Apr) must be rejected
        String[] invalidPeriods = {"202602","202604","202606","202608","202610","202612"};
        for (String period : invalidPeriods) {
            InvoiceAdjustmentValidator.ValidationResult r =
                InvoiceAdjustmentValidator.validate(1, "ALLOWANCE", amount, taxPart, period);
            assertFalse("Period " + period + " (even month) must be invalid", r.valid);
        }

        // Verify Table_Name constant
        assertEquals("TW_InvoiceAdjustment", MInvoiceAdjustment.Table_Name);
    }

    // -----------------------------------------------------------------------
    // 3. testMixedBusinessAdjustment
    //    Verifies: TaxStatementValidator mixed-business apportionment
    //              computeEffectiveInputTax logic
    // -----------------------------------------------------------------------

    /**
     * A mixed-business operator (兼營營業人) must have input-tax apportioned
     * by the supplied ratio.  The resolved TaxPayable must reflect only the
     * deductible portion.
     */
    @Test
    public void testMixedBusinessAdjustment() {
        BigDecimal taxableRevenue  = new BigDecimal("800000.00");
        BigDecimal exemptRevenue   = new BigDecimal("200000.00");
        BigDecimal outputTax       = new BigDecimal("40000.00");  // 5% of taxable
        BigDecimal inputTax        = new BigDecimal("20000.00");
        BigDecimal mixedRatio      = new BigDecimal("0.80");      // 80% taxable

        TaxStatementValidator.ValidationResult result = TaxStatementValidator.validate(
            1, 2026,
            taxableRevenue, exemptRevenue,
            outputTax, inputTax,
            true,   // isMixedBusiness
            mixedRatio);

        assertTrue("Mixed-business statement must be valid", result.valid);
        assertNull("No error on valid mixed-business statement", result.errorMessage);

        // TaxPayable = outputTax - (inputTax * ratio) = 40000 - (20000 * 0.80) = 24000
        BigDecimal expectedPayable = new BigDecimal("24000.000");
        assertEquals("TaxPayable for mixed-business must apply ratio",
            0, expectedPayable.compareTo(result.resolvedTaxPayable));

        // Verify computeEffectiveInputTax helper directly
        BigDecimal effectiveInput = TaxStatementValidator.computeEffectiveInputTax(
            inputTax, true, mixedRatio);
        assertEquals("Effective input tax must be inputTax * ratio",
            0, new BigDecimal("16000.00").compareTo(effectiveInput));

        // Ordinary business: full input tax deductible
        BigDecimal effectiveInputOrdinary = TaxStatementValidator.computeEffectiveInputTax(
            inputTax, false, null);
        assertEquals("Ordinary business: effective input = full inputTax",
            0, inputTax.compareTo(effectiveInputOrdinary));
    }

    // -----------------------------------------------------------------------
    // 4. testAccountingCodeMapping
    //    Verifies: AccountingCodeMapper lookup for all five accounts
    //              AccountingCodeMapperValidator passes for complete chart
    //              Graceful null return for missing account
    // -----------------------------------------------------------------------

    /**
     * The accounting code mapper must resolve all five mandatory Taiwan chart-
     * of-accounts codes for a fully-configured tenant, and the validator must
     * report success.
     */
    @Test
    public void testAccountingCodeMapping() {
        // Verify all five accounts resolve for Org A
        AccountInfo inputTax  = mapper.getInputTaxAccount(ORG_A_CLIENT_ID, CTX, TRX);
        AccountInfo outputTax = mapper.getOutputTaxAccount(ORG_A_CLIENT_ID, CTX, TRX);
        AccountInfo taxRev    = mapper.getTaxableRevenueAccount(ORG_A_CLIENT_ID, CTX, TRX);
        AccountInfo exRev     = mapper.getExemptRevenueAccount(ORG_A_CLIENT_ID, CTX, TRX);
        AccountInfo cogs      = mapper.getCogsAccount(ORG_A_CLIENT_ID, CTX, TRX);

        assertNotNull("Input tax account must be found", inputTax);
        assertNotNull("Output tax account must be found", outputTax);
        assertNotNull("Taxable revenue account must be found", taxRev);
        assertNotNull("Exempt revenue account must be found", exRev);
        assertNotNull("COGS account must be found", cogs);

        // Verify account values match the constants
        assertEquals(AccountingCodes.INPUT_TAX_ACCOUNT_VALUE,     inputTax.value);
        assertEquals(AccountingCodes.OUTPUT_TAX_ACCOUNT_VALUE,    outputTax.value);
        assertEquals(AccountingCodes.TAXABLE_REVENUE_ACCOUNT_VALUE, taxRev.value);
        assertEquals(AccountingCodes.EXEMPT_REVENUE_ACCOUNT_VALUE,  exRev.value);
        assertEquals(AccountingCodes.COGS_ACCOUNT_VALUE,           cogs.value);

        // Verify account types
        assertEquals("Input tax must be Asset",       "A", inputTax.accountType);
        assertEquals("Output tax must be Liability",  "L", outputTax.accountType);
        assertEquals("Taxable revenue must be Revenue", "R", taxRev.accountType);
        assertEquals("Exempt revenue must be Revenue",  "R", exRev.accountType);
        assertEquals("COGS must be Expense",            "E", cogs.accountType);

        // Validator passes for complete chart
        AccountingCodeMapperValidator.ValidationResult valResult =
            mapperValidator.validateAllRequiredAccounts(ORG_A_CLIENT_ID, CTX, TRX);
        assertTrue("Full chart of accounts must pass validator", valResult.valid);
        assertTrue("No errors expected", valResult.errors.isEmpty());

        // Lookup for unknown code must return null gracefully
        AccountInfo unknown = mapper.lookupAccount(ORG_A_CLIENT_ID, "9999", CTX, TRX);
        assertNull("Unknown account code must return null", unknown);
    }

    // -----------------------------------------------------------------------
    // 5. testTaxStatementCalculation
    //    Verifies: All 6 bimonthly periods are valid
    //              TaxPayable computed correctly for ordinary business
    //              Invalid period (0, 7) rejected
    // -----------------------------------------------------------------------

    /**
     * Tests the tax statement calculation for all six bimonthly reporting
     * periods of a calendar year, verifying TaxPayable is correct for each.
     */
    @Test
    public void testTaxStatementCalculation() {
        BigDecimal taxableRevenue = new BigDecimal("1000000.00");
        BigDecimal exemptRevenue  = BigDecimal.ZERO;
        BigDecimal outputTax      = new BigDecimal("50000.00");   // 5% of 1,000,000
        BigDecimal inputTax       = new BigDecimal("20000.00");
        BigDecimal expectedPayable = new BigDecimal("30000.00");  // 50000 - 20000

        // All 6 periods must be valid
        for (int period = 1; period <= 6; period++) {
            TaxStatementValidator.ValidationResult result = TaxStatementValidator.validate(
                period, 2026,
                taxableRevenue, exemptRevenue,
                outputTax, inputTax,
                false, null);

            assertTrue("Period " + period + " of 6 must be valid", result.valid);
            assertEquals("TaxPayable for period " + period + " must be 30000",
                0, expectedPayable.compareTo(result.resolvedTaxPayable));
        }

        // Period 0 must be rejected
        TaxStatementValidator.ValidationResult zeroPeriod = TaxStatementValidator.validate(
            0, 2026, taxableRevenue, exemptRevenue, outputTax, inputTax, false, null);
        assertFalse("Period 0 must be invalid", zeroPeriod.valid);

        // Period 7 must be rejected
        TaxStatementValidator.ValidationResult sevenPeriod = TaxStatementValidator.validate(
            7, 2026, taxableRevenue, exemptRevenue, outputTax, inputTax, false, null);
        assertFalse("Period 7 must be invalid", sevenPeriod.valid);

        // Year < 2000 must be rejected
        TaxStatementValidator.ValidationResult oldYear = TaxStatementValidator.validate(
            1, 1999, taxableRevenue, exemptRevenue, outputTax, inputTax, false, null);
        assertFalse("Year 1999 must be invalid", oldYear.valid);

        // Verify Table_Name constant and period helper
        assertEquals("TW_TaxStatement", MTaxStatement.Table_Name);
        assertTrue("Period 1 is valid", TaxStatementValidator.isValidStatementPeriod(1));
        assertTrue("Period 6 is valid", TaxStatementValidator.isValidStatementPeriod(6));
        assertFalse("Period 0 is invalid", TaxStatementValidator.isValidStatementPeriod(0));
        assertFalse("Period 7 is invalid", TaxStatementValidator.isValidStatementPeriod(7));
    }

    // -----------------------------------------------------------------------
    // 6. testInputTaxExpiry
    //    Verifies: InvoicePrefixMapValidator.isExpiryWarning()
    //              Invoice older than 10 years minus 90 days triggers warning
    //              Recent invoice does not trigger warning
    // -----------------------------------------------------------------------

    /**
     * An invoice issued more than 9 years and 9 months ago (within 90 days of
     * the 10-year input-tax claim deadline) must trigger an expiry warning;
     * a recently-issued invoice must not.
     */
    @Test
    public void testInputTaxExpiry() {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        // Invoice issued today — no warning
        assertFalse("Brand-new invoice must NOT trigger expiry warning",
            InvoicePrefixMapValidator.isExpiryWarning(now, now));

        // Invoice issued exactly 1 year ago — no warning
        Calendar oneYearAgo = Calendar.getInstance();
        oneYearAgo.add(Calendar.YEAR, -1);
        Timestamp oneYearAgoTs = new Timestamp(oneYearAgo.getTimeInMillis());
        assertFalse("1-year-old invoice must NOT trigger expiry warning",
            InvoicePrefixMapValidator.isExpiryWarning(oneYearAgoTs, now));

        // Invoice issued 9 years and 11 months ago — clearly within 90-day warning window
        // (only ~1 month from the 10-year statutory expiry)
        Calendar nineYearsElevenMonthsAgo = Calendar.getInstance();
        nineYearsElevenMonthsAgo.add(Calendar.YEAR, -9);
        nineYearsElevenMonthsAgo.add(Calendar.MONTH, -11);
        Timestamp nearExpiryTs = new Timestamp(nineYearsElevenMonthsAgo.getTimeInMillis());
        assertTrue("Invoice near 10-year boundary MUST trigger expiry warning",
            InvoicePrefixMapValidator.isExpiryWarning(nearExpiryTs, now));

        // Invoice issued exactly 10 years ago — must trigger warning
        Calendar tenYearsAgo = Calendar.getInstance();
        tenYearsAgo.add(Calendar.YEAR, -10);
        Timestamp tenYearsAgoTs = new Timestamp(tenYearsAgo.getTimeInMillis());
        assertTrue("Invoice exactly 10 years old MUST trigger expiry warning",
            InvoicePrefixMapValidator.isExpiryWarning(tenYearsAgoTs, now));

        // Null safety
        assertFalse("null dateInvoiced must not throw; returns false",
            InvoicePrefixMapValidator.isExpiryWarning(null, now));
        assertFalse("null now must not throw; returns false",
            InvoicePrefixMapValidator.isExpiryWarning(now, null));

        // Full validation with expiry warning embedded in result
        InvoicePrefixMapValidator.ValidationResult warnResult =
            InvoicePrefixMapValidator.validate(10, 200, "ZZ0000001", nearExpiryTs, now);
        assertTrue("Validation must still pass (warning is informational)", warnResult.valid);
        assertEquals("Expiry warning must be Y", "Y", warnResult.resolvedIsExpiryWarning);
    }

    // -----------------------------------------------------------------------
    // 7. testValidationChain
    //    Verifies: All four validators reject invalid data with clear messages
    //              Error messages contain the relevant field name
    // -----------------------------------------------------------------------

    /**
     * Exercises the full validation chain: all four validators must cooperate,
     * each rejecting invalid inputs with clear, field-named error messages.
     */
    @Test
    public void testValidationChain() {

        // --- InvoicePrefixValidator ---
        // Bad prefix code
        InvoicePrefixValidator.ValidationResult badCode =
            InvoicePrefixValidator.validate("aa", 1, 100, 0, true);
        assertFalse("Lowercase prefix code must fail", badCode.valid);
        assertTrue("Error must mention PrefixCode", badCode.errorMessage.contains("PrefixCode"));

        // Bad start number
        InvoicePrefixValidator.ValidationResult badStart =
            InvoicePrefixValidator.validate("AA", 0, 100, 0, true);
        assertFalse("StartNumber=0 must fail", badStart.valid);
        assertTrue("Error must mention StartNumber", badStart.errorMessage.contains("StartNumber"));

        // End < Start
        InvoicePrefixValidator.ValidationResult badRange =
            InvoicePrefixValidator.validate("AA", 100, 50, 0, true);
        assertFalse("EndNumber < StartNumber must fail", badRange.valid);
        assertTrue("Error must mention EndNumber", badRange.errorMessage.contains("EndNumber"));

        // --- InvoicePrefixMapValidator ---
        Timestamp now = new Timestamp(System.currentTimeMillis());

        // Zero prefix FK
        InvoicePrefixMapValidator.ValidationResult badPrefixId =
            InvoicePrefixMapValidator.validate(0, 1, "AA0000001", now, now);
        assertFalse("Zero TW_InvoicePrefix_ID must fail", badPrefixId.valid);
        assertTrue("Error must mention TW_InvoicePrefix_ID",
            badPrefixId.errorMessage.contains("TW_InvoicePrefix_ID"));

        // Invalid invoice number format (3 letters instead of 2)
        InvoicePrefixMapValidator.ValidationResult badNum =
            InvoicePrefixMapValidator.validate(1, 1, "ABC0000001", now, now);
        assertFalse("3-letter prefix in invoice number must fail", badNum.valid);
        assertTrue("Error must mention InvoiceNumber",
            badNum.errorMessage.contains("InvoiceNumber"));

        // Null DateInvoiced
        InvoicePrefixMapValidator.ValidationResult nullDate =
            InvoicePrefixMapValidator.validate(1, 1, "AA0000001", null, now);
        assertFalse("Null DateInvoiced must fail", nullDate.valid);
        assertTrue("Error must mention DateInvoiced",
            nullDate.errorMessage.contains("DateInvoiced"));

        // --- InvoiceAdjustmentValidator ---
        // Invalid adjustment type
        InvoiceAdjustmentValidator.ValidationResult badType =
            InvoiceAdjustmentValidator.validate(1, "CREDIT",
                new BigDecimal("500"), BigDecimal.ZERO, "202601");
        assertFalse("Unknown AdjustmentType must fail", badType.valid);
        assertTrue("Error must mention AdjustmentType",
            badType.errorMessage.contains("AdjustmentType"));

        // Negative adjustment amount
        InvoiceAdjustmentValidator.ValidationResult badAmount =
            InvoiceAdjustmentValidator.validate(1, "RETURN",
                new BigDecimal("-100"), BigDecimal.ZERO, "202601");
        assertFalse("Negative AdjustmentAmount must fail", badAmount.valid);
        assertTrue("Error must mention AdjustmentAmount",
            badAmount.errorMessage.contains("AdjustmentAmount"));

        // InputTax > AdjustmentAmount
        InvoiceAdjustmentValidator.ValidationResult taxOverflow =
            InvoiceAdjustmentValidator.validate(1, "RETURN",
                new BigDecimal("100"), new BigDecimal("200"), "202601");
        assertFalse("InputTaxAmount > AdjustmentAmount must fail", taxOverflow.valid);

        // --- TaxStatementValidator ---
        // Negative TaxableRevenue
        TaxStatementValidator.ValidationResult negRevenue = TaxStatementValidator.validate(
            1, 2026,
            new BigDecimal("-1"), BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO,
            false, null);
        assertFalse("Negative TaxableRevenue must fail", negRevenue.valid);
        assertTrue("Error must mention TaxableRevenue",
            negRevenue.errorMessage.contains("TaxableRevenue"));

        // Mixed-business with null ratio
        TaxStatementValidator.ValidationResult nullRatio = TaxStatementValidator.validate(
            1, 2026,
            BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO,
            true, null);  // isMixedBusiness=true but ratio=null
        assertFalse("MixedBusiness=true with null ratio must fail", nullRatio.valid);
        assertTrue("Error must mention MixedBusinessRatio",
            nullRatio.errorMessage.contains("MixedBusinessRatio"));

        // Non-mixed-business with a ratio set must fail
        TaxStatementValidator.ValidationResult spuriousRatio = TaxStatementValidator.validate(
            1, 2026,
            BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO,
            false, new BigDecimal("0.50"));  // ratio should be null for non-mixed
        assertFalse("Non-mixed business with ratio must fail", spuriousRatio.valid);
    }

    // -----------------------------------------------------------------------
    // 8. testTenantIsolation
    //    Verifies: AccountingCodeMapper isolates data per AD_Client_ID
    //              Cache entries are independent per tenant
    //              Same account code may have different element value IDs
    //              Invalidating one tenant does not affect another
    // -----------------------------------------------------------------------

    /**
     * Verifies that two organisations (different AD_Client_ID values) each see
     * their own independent accounting data and that their caches are fully
     * isolated.
     */
    @Test
    public void testTenantIsolation() {
        // Both organisations have account 1112 but with different C_ElementValue_IDs
        AccountInfo orgAInputTax = mapper.getInputTaxAccount(ORG_A_CLIENT_ID, CTX, TRX);
        AccountInfo orgBInputTax = mapper.getInputTaxAccount(ORG_B_CLIENT_ID, CTX, TRX);

        assertNotNull("Org A must have input tax account", orgAInputTax);
        assertNotNull("Org B must have input tax account", orgBInputTax);

        // Same account value
        assertEquals("Both orgs share the same account value",
            orgAInputTax.value, orgBInputTax.value);

        // But different C_ElementValue_IDs (different DB masters per tenant)
        assertFalse("Org A and Org B must have different elementValueIds",
            orgAInputTax.elementValueId == orgBInputTax.elementValueId);

        // Invalidate Org A cache; Org B cache should remain intact
        assertEquals("Both tenants cached before invalidation", 2, mapper.getCacheSize());
        mapper.invalidateCache(ORG_A_CLIENT_ID);
        assertEquals("Only Org B remains in cache after Org A invalidated",
            1, mapper.getCacheSize());

        // After re-loading Org A, both should be cached again
        mapper.getInputTaxAccount(ORG_A_CLIENT_ID, CTX, TRX);
        assertEquals("Both tenants back in cache", 2, mapper.getCacheSize());

        // Validate org B independently — must still pass
        AccountingCodeMapperValidator.ValidationResult orgBResult =
            mapperValidator.validateAllRequiredAccounts(ORG_B_CLIENT_ID, CTX, TRX);
        assertTrue("Org B chart of accounts validation must pass independently",
            orgBResult.valid);

        // Adding Org A with an incomplete chart should not affect Org B
        StubAccountingCodeMapper partialMapper = new StubAccountingCodeMapper();
        Map<String, AccountInfo> incompleteAccounts = new HashMap<>();
        incompleteAccounts.put("1112", new AccountInfo("1112", "應收進項稅額", "A", 9001));
        // Missing 2121, 4100, 4200, 5100
        partialMapper.registerAccounts(ORG_A_CLIENT_ID, incompleteAccounts);
        registerFullChartOfAccounts(ORG_B_CLIENT_ID);  // Ensure Org B still complete in field mapper
        partialMapper.registerAccounts(ORG_B_CLIENT_ID,
            mapper.getCachedAccountsForClient(ORG_B_CLIENT_ID));

        AccountingCodeMapperValidator partialValidator =
            new AccountingCodeMapperValidator(partialMapper);

        AccountingCodeMapperValidator.ValidationResult orgAPartial =
            partialValidator.validateAllRequiredAccounts(ORG_A_CLIENT_ID, CTX, TRX);
        assertFalse("Org A with incomplete chart must fail validation", orgAPartial.valid);
        assertEquals("Org A must have 4 missing-account errors",
            4, orgAPartial.errors.size());

        AccountingCodeMapperValidator.ValidationResult orgBFull =
            partialValidator.validateAllRequiredAccounts(ORG_B_CLIENT_ID, CTX, TRX);
        assertTrue("Org B full chart must still pass validation independently",
            orgBFull.valid);

        // Cache invalidation: clear all and confirm empty
        mapper.invalidateAllCaches();
        assertEquals("All caches cleared", 0, mapper.getCacheSize());
    }

    // -----------------------------------------------------------------------
    // 9. End-to-End: Invoice -> Adjustment -> Tax Statement
    //    Verifies the complete Phase 1 data-flow in sequence
    // -----------------------------------------------------------------------

    /**
     * End-to-end scenario: simulates the journey from invoice issuance through
     * an allowance adjustment to a bimonthly tax statement.
     *
     * <ol>
     *   <li>A valid invoice prefix (AA, 1–99999999) is created.</li>
     *   <li>The first invoice AA0000001 is mapped to the prefix.</li>
     *   <li>An allowance (折讓) adjustment is recorded against that invoice.</li>
     *   <li>A tax statement for period 1/2026 aggregates the results.</li>
     *   <li>All validators are called in the correct order; all must pass.</li>
     * </ol>
     */
    @Test
    public void testEndToEndInvoiceAdjustmentTaxStatement() {
        // Step 1 – Prefix
        InvoicePrefixValidator.ValidationResult prefixVal =
            InvoicePrefixValidator.validate("AA", 1, 99999999, 0, true);
        assertTrue("Step 1: prefix validation passes", prefixVal.valid);
        int currentNumber = prefixVal.resolvedCurrentNumber;  // = 1
        assertEquals("CurrentNumber initialised to StartNumber", 1, currentNumber);

        // Step 2 – Prefix Map (link invoice C_Invoice_ID=1001 to prefix)
        Timestamp invoiceDate = new Timestamp(System.currentTimeMillis());
        String invoiceNumber  = "AA" + String.format("%07d", currentNumber); // AA0000001
        InvoicePrefixMapValidator.ValidationResult mapVal =
            InvoicePrefixMapValidator.validate(1, 1001, invoiceNumber, invoiceDate, invoiceDate);
        assertTrue("Step 2: prefix-map validation passes", mapVal.valid);
        assertEquals("No expiry warning for new invoice", "N", mapVal.resolvedIsExpiryWarning);

        // Step 3 – Allowance adjustment (TW_InvoicePrefixMap_ID=1, amount=5000, tax=250)
        BigDecimal adjAmount = new BigDecimal("5000.00");
        BigDecimal adjTax    = new BigDecimal("250.00");
        InvoiceAdjustmentValidator.ValidationResult adjVal =
            InvoiceAdjustmentValidator.validate(1, "ALLOWANCE", adjAmount, adjTax, "202601");
        assertTrue("Step 3: adjustment validation passes", adjVal.valid);

        // Step 4 – Tax Statement (period 1 of 2026)
        BigDecimal taxableRevenue = new BigDecimal("100000.00");
        BigDecimal outputTax      = new BigDecimal("5000.00");
        BigDecimal inputTax       = new BigDecimal("2000.00");
        TaxStatementValidator.ValidationResult stmtVal = TaxStatementValidator.validate(
            1, 2026,
            taxableRevenue, BigDecimal.ZERO,
            outputTax, inputTax,
            false, null);
        assertTrue("Step 4: tax statement validation passes", stmtVal.valid);

        // TaxPayable = 5000 - 2000 = 3000
        BigDecimal expectedPayable = new BigDecimal("3000.00");
        assertEquals("Step 4: TaxPayable correctly computed",
            0, expectedPayable.compareTo(stmtVal.resolvedTaxPayable));

        // Step 5 – Verify accounting codes are available for Org A
        AccountInfo inputTaxAccount = mapper.getInputTaxAccount(ORG_A_CLIENT_ID, CTX, TRX);
        AccountInfo outputTaxAccount = mapper.getOutputTaxAccount(ORG_A_CLIENT_ID, CTX, TRX);
        assertNotNull("Step 5: Input tax account available", inputTaxAccount);
        assertNotNull("Step 5: Output tax account available", outputTaxAccount);
        assertTrue("Step 5: AccountingCodeMapper has account mapped",
            mapper.validateAccountExists(ORG_A_CLIENT_ID, "1112", CTX, TRX));
    }

    // -----------------------------------------------------------------------
    // 10. testIsValidPrefixCode edge cases
    // -----------------------------------------------------------------------

    /**
     * Verifies all valid and invalid prefix code values against the canonical
     * {@link InvoicePrefixValidator#isValidPrefixCode(String)} rule.
     */
    @Test
    public void testIsValidPrefixCode() {
        // Valid — exactly 2 uppercase letters
        assertTrue("AA valid", InvoicePrefixValidator.isValidPrefixCode("AA"));
        assertTrue("ZZ valid", InvoicePrefixValidator.isValidPrefixCode("ZZ"));
        assertTrue("AB valid", InvoicePrefixValidator.isValidPrefixCode("AB"));

        // Verify the validator's behaviour is accessible through the static API
        assertTrue("ZA valid",  InvoicePrefixValidator.isValidPrefixCode("ZA"));

        // Invalid
        assertFalse("null invalid",   InvoicePrefixValidator.isValidPrefixCode(null));
        assertFalse("empty invalid",  InvoicePrefixValidator.isValidPrefixCode(""));
        assertFalse("1 letter",       InvoicePrefixValidator.isValidPrefixCode("A"));
        assertFalse("3 letters",      InvoicePrefixValidator.isValidPrefixCode("ABC"));
        assertFalse("lowercase",      InvoicePrefixValidator.isValidPrefixCode("aa"));
        assertFalse("mixed case",     InvoicePrefixValidator.isValidPrefixCode("Aa"));
        assertFalse("with digit",     InvoicePrefixValidator.isValidPrefixCode("A1"));
        assertFalse("number only",    InvoicePrefixValidator.isValidPrefixCode("12"));

        // isValidNumberRange
        assertTrue("1-100 valid",     InvoicePrefixValidator.isValidNumberRange(1, 100));
        assertTrue("1-1 valid",       InvoicePrefixValidator.isValidNumberRange(1, 1));
        assertTrue("Large range", InvoicePrefixValidator.isValidNumberRange(1, 99999999));
        assertFalse("0-100 invalid",  InvoicePrefixValidator.isValidNumberRange(0, 100));
        assertFalse("5-3 invalid",    InvoicePrefixValidator.isValidNumberRange(5, 3));
    }

    // -----------------------------------------------------------------------
    // 11. testInvoiceNumberFormat
    // -----------------------------------------------------------------------

    /**
     * Verifies that the invoice number format validator accepts exactly 2
     * uppercase letters followed by 7 or 8 digits, and rejects all other forms.
     */
    @Test
    public void testInvoiceNumberFormat() {
        // Valid — 7 digits
        assertTrue("AA0000001 valid",
            InvoicePrefixMapValidator.isValidInvoiceNumber("AA0000001"));
        // Valid — 8 digits
        assertTrue("AA00000001 valid",
            InvoicePrefixMapValidator.isValidInvoiceNumber("AA00000001"));

        // Invalid cases
        assertFalse("null invalid",
            InvoicePrefixMapValidator.isValidInvoiceNumber(null));
        assertFalse("6 digits invalid",
            InvoicePrefixMapValidator.isValidInvoiceNumber("AA000001"));
        assertFalse("9 digits invalid",
            InvoicePrefixMapValidator.isValidInvoiceNumber("AA000000001"));
        assertFalse("lowercase letters",
            InvoicePrefixMapValidator.isValidInvoiceNumber("aa0000001"));
        assertFalse("3 letters prefix",
            InvoicePrefixMapValidator.isValidInvoiceNumber("AAA0000001"));
        assertFalse("no letters prefix",
            InvoicePrefixMapValidator.isValidInvoiceNumber("000000001"));
    }
}
