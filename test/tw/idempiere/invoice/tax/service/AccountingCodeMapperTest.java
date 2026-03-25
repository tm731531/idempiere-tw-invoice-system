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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import tw.idempiere.invoice.tax.service.AccountingCodeMapper.AccountInfo;
import tw.idempiere.invoice.tax.service.AccountingCodeMapperValidator.ValidationResult;
import tw.idempiere.invoice.tax.util.AccountingCodes;

/**
 * Unit tests for {@link AccountingCodeMapper} and
 * {@link AccountingCodeMapperValidator}.
 *
 * <p>Because {@link AccountingCodeMapper#loadAccountsFromDB} calls into
 * iDempiere's {@code DB} utility (which requires an OSGi runtime and a live
 * database), these tests use a <em>test subclass</em> that overrides
 * {@code loadAccountsFromDB} with an in-memory stub.  All cache and lookup
 * logic is exercised without any iDempiere dependency.</p>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public class AccountingCodeMapperTest {

    // -----------------------------------------------------------------------
    // Test constants
    // -----------------------------------------------------------------------

    private static final int CLIENT_A = 1000;
    private static final int CLIENT_B = 2000;
    private static final int CLIENT_EMPTY = 3000;

    private static final Properties CTX = new Properties();
    private static final String TRX = null;

    // -----------------------------------------------------------------------
    // Stub subclass – replaces DB query with in-memory data
    // -----------------------------------------------------------------------

    /**
     * Testable subclass of {@link AccountingCodeMapper} that replaces the
     * database query with a configurable in-memory fixture.
     */
    private static class StubAccountingCodeMapper extends AccountingCodeMapper {

        /** Per-client stub data: clientId -> (accountValue -> AccountInfo). */
        private final Map<Integer, Map<String, AccountInfo>> stubData = new HashMap<>();

        /**
         * Registers a set of accounts for a given client.
         *
         * @param clientId the client to register data for
         * @param accounts map of accountValue -> AccountInfo
         */
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
    // Test fixtures
    // -----------------------------------------------------------------------

    private StubAccountingCodeMapper mapper;

    /**
     * Builds a complete standard Taiwan chart-of-accounts fixture for the
     * given client ID and registers it in the stub mapper.
     */
    private void registerFullChartOfAccounts(int clientId) {
        Map<String, AccountInfo> accounts = new HashMap<>();
        accounts.put("1112", new AccountInfo("1112", "應收進項稅額", "A", 1112));
        accounts.put("2121", new AccountInfo("2121", "應付營業稅",   "L", 2121));
        accounts.put("4100", new AccountInfo("4100", "銷售收入-應稅", "R", 4100));
        accounts.put("4200", new AccountInfo("4200", "銷售收入-免稅", "R", 4200));
        accounts.put("5100", new AccountInfo("5100", "銷貨成本",      "E", 5100));
        mapper.registerAccounts(clientId, accounts);
    }

    @Before
    public void setUp() {
        mapper = new StubAccountingCodeMapper();

        // Client A has all five required accounts
        registerFullChartOfAccounts(CLIENT_A);

        // Client B has a partially different chart of accounts (missing COGS,
        // and 2121 has wrong type – simulates a non-standard chart)
        Map<String, AccountInfo> clientBAccounts = new HashMap<>();
        clientBAccounts.put("1112", new AccountInfo("1112", "應收進項稅額", "A", 5112));
        clientBAccounts.put("2121", new AccountInfo("2121", "應付營業稅",   "A", 5121)); // wrong type: A instead of L
        clientBAccounts.put("4100", new AccountInfo("4100", "銷售收入-應稅", "R", 5400));
        clientBAccounts.put("4200", new AccountInfo("4200", "銷售收入-免稅", "R", 5420));
        // 5100 intentionally missing in Client B
        mapper.registerAccounts(CLIENT_B, clientBAccounts);

        // CLIENT_EMPTY has no accounts registered
    }

    // -----------------------------------------------------------------------
    // testGetInputTaxAccount
    // -----------------------------------------------------------------------

    /**
     * A complete chart of accounts must return the correct Input Tax account.
     */
    @Test
    public void testGetInputTaxAccount() {
        AccountInfo info = mapper.getInputTaxAccount(CLIENT_A, CTX, TRX);

        assertNotNull("Input tax account must be found", info);
        assertEquals("Account value must be 1112",
            AccountingCodes.INPUT_TAX_ACCOUNT_VALUE, info.value);
        assertEquals("Account type must be Asset",
            AccountingCodes.ACCOUNT_TYPE_ASSET, info.accountType);
        assertEquals("Account name should match fixture",
            "應收進項稅額", info.name);
        assertEquals("C_ElementValue_ID must match",
            1112, info.elementValueId);
    }

    // -----------------------------------------------------------------------
    // testGetOutputTaxAccount
    // -----------------------------------------------------------------------

    /**
     * A complete chart of accounts must return the correct Output Tax account.
     */
    @Test
    public void testGetOutputTaxAccount() {
        AccountInfo info = mapper.getOutputTaxAccount(CLIENT_A, CTX, TRX);

        assertNotNull("Output tax account must be found", info);
        assertEquals("Account value must be 2121",
            AccountingCodes.OUTPUT_TAX_ACCOUNT_VALUE, info.value);
        assertEquals("Account type must be Liability",
            AccountingCodes.ACCOUNT_TYPE_LIABILITY, info.accountType);
    }

    // -----------------------------------------------------------------------
    // testGetTaxableRevenueAccount
    // -----------------------------------------------------------------------

    /**
     * A complete chart of accounts must return the correct Taxable Revenue account.
     */
    @Test
    public void testGetTaxableRevenueAccount() {
        AccountInfo info = mapper.getTaxableRevenueAccount(CLIENT_A, CTX, TRX);

        assertNotNull("Taxable revenue account must be found", info);
        assertEquals("Account value must be 4100",
            AccountingCodes.TAXABLE_REVENUE_ACCOUNT_VALUE, info.value);
        assertEquals("Account type must be Revenue",
            AccountingCodes.ACCOUNT_TYPE_REVENUE, info.accountType);
    }

    // -----------------------------------------------------------------------
    // testGetExemptRevenueAccount
    // -----------------------------------------------------------------------

    /**
     * A complete chart of accounts must return the correct Exempt Revenue account.
     */
    @Test
    public void testGetExemptRevenueAccount() {
        AccountInfo info = mapper.getExemptRevenueAccount(CLIENT_A, CTX, TRX);

        assertNotNull("Exempt revenue account must be found", info);
        assertEquals("Account value must be 4200",
            AccountingCodes.EXEMPT_REVENUE_ACCOUNT_VALUE, info.value);
        assertEquals("Account type must be Revenue",
            AccountingCodes.ACCOUNT_TYPE_REVENUE, info.accountType);
    }

    // -----------------------------------------------------------------------
    // testGetCogsAccount
    // -----------------------------------------------------------------------

    /**
     * A complete chart of accounts must return the correct COGS account.
     */
    @Test
    public void testGetCogsAccount() {
        AccountInfo info = mapper.getCogsAccount(CLIENT_A, CTX, TRX);

        assertNotNull("COGS account must be found", info);
        assertEquals("Account value must be 5100",
            AccountingCodes.COGS_ACCOUNT_VALUE, info.value);
        assertEquals("Account type must be Expense",
            AccountingCodes.ACCOUNT_TYPE_EXPENSE, info.accountType);
    }

    // -----------------------------------------------------------------------
    // testAccountNotFound
    // -----------------------------------------------------------------------

    /**
     * When a chart of accounts does not contain a required code, the lookup
     * must return {@code null} without throwing an exception.
     */
    @Test
    public void testAccountNotFound_MissingCogs() {
        // Client B does not have account 5100
        AccountInfo info = mapper.getCogsAccount(CLIENT_B, CTX, TRX);

        assertNull("COGS must be null when absent from chart of accounts", info);
    }

    /**
     * When the chart of accounts is completely empty, every lookup must return
     * {@code null}.
     */
    @Test
    public void testAccountNotFound_EmptyChartOfAccounts() {
        assertNull("Input tax null for empty chart",
            mapper.getInputTaxAccount(CLIENT_EMPTY, CTX, TRX));
        assertNull("Output tax null for empty chart",
            mapper.getOutputTaxAccount(CLIENT_EMPTY, CTX, TRX));
        assertNull("Taxable revenue null for empty chart",
            mapper.getTaxableRevenueAccount(CLIENT_EMPTY, CTX, TRX));
        assertNull("Exempt revenue null for empty chart",
            mapper.getExemptRevenueAccount(CLIENT_EMPTY, CTX, TRX));
        assertNull("COGS null for empty chart",
            mapper.getCogsAccount(CLIENT_EMPTY, CTX, TRX));
    }

    /**
     * {@code lookupAccount} with a null value must return {@code null} gracefully.
     */
    @Test
    public void testLookupAccount_NullValue() {
        assertNull("null account value must return null",
            mapper.lookupAccount(CLIENT_A, null, CTX, TRX));
    }

    /**
     * {@code lookupAccount} with an empty string value must return {@code null}.
     */
    @Test
    public void testLookupAccount_EmptyValue() {
        assertNull("empty account value must return null",
            mapper.lookupAccount(CLIENT_A, "", CTX, TRX));
    }

    // -----------------------------------------------------------------------
    // testValidateAccountExists
    // -----------------------------------------------------------------------

    /**
     * {@code validateAccountExists} must return {@code true} for an account
     * present in the chart of accounts.
     */
    @Test
    public void testValidateAccountExists_Present() {
        assertTrue("1112 exists in Client A",
            mapper.validateAccountExists(CLIENT_A, "1112", CTX, TRX));
    }

    /**
     * {@code validateAccountExists} must return {@code false} for a code that
     * is absent from the chart of accounts.
     */
    @Test
    public void testValidateAccountExists_Absent() {
        assertFalse("9999 does not exist in Client A",
            mapper.validateAccountExists(CLIENT_A, "9999", CTX, TRX));
    }

    // -----------------------------------------------------------------------
    // testCaching
    // -----------------------------------------------------------------------

    /**
     * The cache must start empty and grow as tenants are queried.
     */
    @Test
    public void testCaching_CachePopulatedAfterFirstLookup() {
        StubAccountingCodeMapper freshMapper = new StubAccountingCodeMapper();
        registerFullChartOfAccounts(CLIENT_A); // registers in the field-level mapper, not freshMapper
        freshMapper.registerAccounts(CLIENT_A, mapper.getCachedAccountsForClient(CLIENT_A));

        assertEquals("Cache empty before any lookup", 0, freshMapper.getCacheSize());

        freshMapper.getInputTaxAccount(CLIENT_A, CTX, TRX);

        assertEquals("Cache has one entry after first lookup", 1, freshMapper.getCacheSize());
    }

    /**
     * Repeated lookups for the same tenant must be served from cache without
     * calling {@code loadAccountsFromDB} a second time.
     */
    @Test
    public void testCaching_SecondLookupHitsCache() {
        // Track number of loadAccountsFromDB calls via a counting subclass
        final int[] loadCount = {0};
        StubAccountingCodeMapper countingMapper = new StubAccountingCodeMapper() {
            @Override
            protected Map<String, AccountInfo> loadAccountsFromDB(int clientId, String trxName) {
                loadCount[0]++;
                Map<String, AccountInfo> data = new HashMap<>();
                data.put("1112", new AccountInfo("1112", "應收進項稅額", "A", 1112));
                return Collections.unmodifiableMap(data);
            }
        };

        countingMapper.getInputTaxAccount(CLIENT_A, CTX, TRX);
        countingMapper.getInputTaxAccount(CLIENT_A, CTX, TRX);
        countingMapper.getInputTaxAccount(CLIENT_A, CTX, TRX);

        assertEquals("loadAccountsFromDB must be called exactly once", 1, loadCount[0]);
    }

    /**
     * After {@code invalidateCache}, the next lookup must re-query the database.
     */
    @Test
    public void testCaching_InvalidateTriggersFreshLoad() {
        final int[] loadCount = {0};
        StubAccountingCodeMapper countingMapper = new StubAccountingCodeMapper() {
            @Override
            protected Map<String, AccountInfo> loadAccountsFromDB(int clientId, String trxName) {
                loadCount[0]++;
                Map<String, AccountInfo> data = new HashMap<>();
                data.put("1112", new AccountInfo("1112", "應收進項稅額", "A", 1112));
                return Collections.unmodifiableMap(data);
            }
        };

        countingMapper.getInputTaxAccount(CLIENT_A, CTX, TRX); // load #1
        countingMapper.invalidateCache(CLIENT_A);
        countingMapper.getInputTaxAccount(CLIENT_A, CTX, TRX); // load #2

        assertEquals("loadAccountsFromDB must be called twice after invalidation", 2, loadCount[0]);
    }

    /**
     * {@code invalidateAllCaches} must clear all tenants, not just one.
     */
    @Test
    public void testCaching_InvalidateAll() {
        mapper.getInputTaxAccount(CLIENT_A, CTX, TRX);
        mapper.getInputTaxAccount(CLIENT_B, CTX, TRX);
        assertEquals("Two tenants cached", 2, mapper.getCacheSize());

        mapper.invalidateAllCaches();
        assertEquals("Cache empty after invalidateAllCaches", 0, mapper.getCacheSize());
    }

    // -----------------------------------------------------------------------
    // testMultipleTenants
    // -----------------------------------------------------------------------

    /**
     * Accounts for different tenants must be isolated: a code present in one
     * tenant's chart must not be found in another's if absent.
     */
    @Test
    public void testMultipleTenants_AccountIsolation() {
        // 5100 exists in Client A but not Client B
        AccountInfo inA = mapper.getCogsAccount(CLIENT_A, CTX, TRX);
        AccountInfo inB = mapper.getCogsAccount(CLIENT_B, CTX, TRX);

        assertNotNull("COGS (5100) must exist in Client A", inA);
        assertNull("COGS (5100) must NOT exist in Client B", inB);
    }

    /**
     * The same account code may map to different {@code C_ElementValue_ID}
     * values across tenants (separate account masters).
     */
    @Test
    public void testMultipleTenants_DifferentElementValueIds() {
        AccountInfo inA = mapper.getInputTaxAccount(CLIENT_A, CTX, TRX);
        AccountInfo inB = mapper.getInputTaxAccount(CLIENT_B, CTX, TRX);

        assertNotNull("Client A must have 1112", inA);
        assertNotNull("Client B must have 1112", inB);
        assertEquals("Same account value", inA.value, inB.value);

        // Different tenants have different primary keys for the same code
        assertFalse("Element value IDs differ between tenants",
            inA.elementValueId == inB.elementValueId);
    }

    /**
     * Cache for one tenant must not affect lookups for another tenant.
     */
    @Test
    public void testMultipleTenants_CacheIsolation() {
        mapper.getInputTaxAccount(CLIENT_A, CTX, TRX); // populate Client A cache
        mapper.invalidateCache(CLIENT_A);              // clear Client A only

        // Client B cache should be unaffected
        assertEquals("Only Client A was invalidated; getCacheSize reflects remaining tenants",
            0, mapper.getCacheSize()); // Client B not yet loaded either

        mapper.getInputTaxAccount(CLIENT_B, CTX, TRX);
        assertEquals("Client B now cached", 1, mapper.getCacheSize());

        mapper.getInputTaxAccount(CLIENT_A, CTX, TRX);
        assertEquals("Both tenants now cached", 2, mapper.getCacheSize());
    }

    // -----------------------------------------------------------------------
    // AccountingCodeMapperValidator tests
    // -----------------------------------------------------------------------

    /**
     * A tenant with all five required accounts and correct types must pass
     * validation with no errors and no warnings.
     */
    @Test
    public void testValidator_AllAccountsPresent_NoErrors() {
        AccountingCodeMapperValidator validator = new AccountingCodeMapperValidator(mapper);
        ValidationResult result = validator.validateAllRequiredAccounts(CLIENT_A, CTX, TRX);

        assertTrue("All accounts present: validation must pass", result.valid);
        assertTrue("No errors expected", result.errors.isEmpty());
        assertTrue("No warnings expected", result.warnings.isEmpty());
    }

    /**
     * A tenant missing one mandatory account must fail validation with a
     * clear error message.
     */
    @Test
    public void testValidator_MissingAccount_FailsWithError() {
        AccountingCodeMapperValidator validator = new AccountingCodeMapperValidator(mapper);
        // Client B is missing 5100
        ValidationResult result = validator.validateAllRequiredAccounts(CLIENT_B, CTX, TRX);

        assertFalse("Missing COGS: validation must fail", result.valid);
        assertEquals("Exactly one error for missing COGS", 1, result.errors.size());
        assertTrue("Error message mentions account code",
            result.errors.get(0).contains("5100"));
        assertTrue("Error message mentions COGS description",
            result.errors.get(0).contains("Cost of Goods Sold"));
        assertTrue("Error message provides guidance",
            result.errors.get(0).contains("ChartOfAccountsTW"));
    }

    /**
     * A tenant with an account of the wrong type must produce a warning (not
     * an error), allowing operation to continue with a non-standard chart.
     */
    @Test
    public void testValidator_WrongAccountType_ProducesWarning() {
        AccountingCodeMapperValidator validator = new AccountingCodeMapperValidator(mapper);
        // Client B has 2121 typed as Asset instead of Liability
        ValidationResult result = validator.validateAllRequiredAccounts(CLIENT_B, CTX, TRX);

        // Should have warnings (wrong type on 2121) but error only for missing 5100
        assertFalse("Missing COGS still causes failure", result.valid);
        assertFalse("Warnings list must not be empty for type mismatch",
            result.warnings.isEmpty());
        assertTrue("Warning mentions account code 2121",
            result.warnings.get(0).contains("2121"));
        assertTrue("Warning mentions expected type",
            result.warnings.get(0).contains("L"));
        assertTrue("Warning mentions found type",
            result.warnings.get(0).contains("A"));
    }

    /**
     * A tenant with an empty chart of accounts must fail with five errors —
     * one for each mandatory account.
     */
    @Test
    public void testValidator_EmptyChartOfAccounts_FiveErrors() {
        AccountingCodeMapperValidator validator = new AccountingCodeMapperValidator(mapper);
        ValidationResult result = validator.validateAllRequiredAccounts(CLIENT_EMPTY, CTX, TRX);

        assertFalse("Empty chart: must fail", result.valid);
        assertEquals("Five errors expected for five missing accounts", 5, result.errors.size());
    }

    /**
     * {@link AccountingCodeMapperValidator#validateSingleAccountExists} must
     * return valid for an account that exists.
     */
    @Test
    public void testValidator_SingleAccount_Exists() {
        AccountingCodeMapperValidator validator = new AccountingCodeMapperValidator(mapper);
        ValidationResult result = validator.validateSingleAccountExists(
            CLIENT_A, "1112", CTX, TRX);

        assertTrue("Account 1112 exists in Client A", result.valid);
        assertTrue("No errors for existing account", result.errors.isEmpty());
    }

    /**
     * {@link AccountingCodeMapperValidator#validateSingleAccountExists} must
     * return invalid for an account that does not exist.
     */
    @Test
    public void testValidator_SingleAccount_DoesNotExist() {
        AccountingCodeMapperValidator validator = new AccountingCodeMapperValidator(mapper);
        ValidationResult result = validator.validateSingleAccountExists(
            CLIENT_A, "9999", CTX, TRX);

        assertFalse("Account 9999 does not exist", result.valid);
        assertFalse("Error message must be present", result.errors.isEmpty());
        assertTrue("Error mentions account value",
            result.errors.get(0).contains("9999"));
    }

    /**
     * {@link AccountingCodeMapperValidator#validateSingleAccountExists} must
     * return invalid for a blank account value.
     */
    @Test
    public void testValidator_SingleAccount_BlankValue() {
        AccountingCodeMapperValidator validator = new AccountingCodeMapperValidator(mapper);
        ValidationResult result = validator.validateSingleAccountExists(
            CLIENT_A, "", CTX, TRX);

        assertFalse("Blank value must be invalid", result.valid);
        assertFalse("Error message must be present", result.errors.isEmpty());
    }

    /**
     * Constructing a {@link AccountingCodeMapperValidator} with a null mapper
     * must throw {@link IllegalArgumentException}.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testValidator_NullMapperThrows() {
        new AccountingCodeMapperValidator(null);
    }

    // -----------------------------------------------------------------------
    // AccountingCodes constants tests
    // -----------------------------------------------------------------------

    /**
     * Verifies the five default account code constants have the correct values.
     */
    @Test
    public void testAccountingCodes_Constants() {
        assertEquals("Input Tax account value",    "1112", AccountingCodes.INPUT_TAX_ACCOUNT_VALUE);
        assertEquals("Output Tax account value",   "2121", AccountingCodes.OUTPUT_TAX_ACCOUNT_VALUE);
        assertEquals("Taxable Revenue account value", "4100", AccountingCodes.TAXABLE_REVENUE_ACCOUNT_VALUE);
        assertEquals("Exempt Revenue account value",  "4200", AccountingCodes.EXEMPT_REVENUE_ACCOUNT_VALUE);
        assertEquals("COGS account value",         "5100", AccountingCodes.COGS_ACCOUNT_VALUE);
    }

    /**
     * Verifies the account type constants match iDempiere's C_ElementValue.AccountType codes.
     */
    @Test
    public void testAccountingCodes_TypeConstants() {
        assertEquals("Asset type code",     "A", AccountingCodes.ACCOUNT_TYPE_ASSET);
        assertEquals("Liability type code", "L", AccountingCodes.ACCOUNT_TYPE_LIABILITY);
        assertEquals("Revenue type code",   "R", AccountingCodes.ACCOUNT_TYPE_REVENUE);
        assertEquals("Expense type code",   "E", AccountingCodes.ACCOUNT_TYPE_EXPENSE);
    }

    /**
     * Verifies that {@code expectedAccountType} returns the correct type for
     * each of the five standard account codes.
     */
    @Test
    public void testAccountingCodes_ExpectedAccountType() {
        assertEquals("1112 -> Asset",
            "A", AccountingCodes.expectedAccountType("1112"));
        assertEquals("2121 -> Liability",
            "L", AccountingCodes.expectedAccountType("2121"));
        assertEquals("4100 -> Revenue",
            "R", AccountingCodes.expectedAccountType("4100"));
        assertEquals("4200 -> Revenue",
            "R", AccountingCodes.expectedAccountType("4200"));
        assertEquals("5100 -> Expense",
            "E", AccountingCodes.expectedAccountType("5100"));
        assertNull("Unknown code -> null",
            AccountingCodes.expectedAccountType("9999"));
        assertNull("null input -> null",
            AccountingCodes.expectedAccountType(null));
    }
}
