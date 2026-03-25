/******************************************************************************
 * Taiwan Invoice Tax System for iDempiere
 * Copyright (C) Taiwan iDempiere Community. All Rights Reserved.
 * License: GNU General Public License version 2
 *****************************************************************************/
package tw.idempiere.invoice.tax.service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.compiere.util.DB;

import tw.idempiere.invoice.tax.util.AccountingCodes;

/**
 * Dynamic accounting code mapper for the Taiwan Invoice Tax System.
 *
 * <p>Resolves Taiwan-specific chart-of-account codes at runtime by querying
 * {@code C_ElementValue} — the iDempiere dictionary table that stores every
 * account defined in a tenant's chart of accounts.  Results are cached
 * per-tenant (keyed on {@code AD_Client_ID}) to minimise database round-trips,
 * while still allowing cache invalidation when account data changes.</p>
 *
 * <h3>Design Principles</h3>
 * <ol>
 *   <li><b>No hard-coded IDs</b> – Account codes are fetched by their
 *       {@code Value} column (e.g. {@code "1112"}), not by their numeric
 *       primary key, so the mapping is portable across iDempiere installations.</li>
 *   <li><b>Tenant isolation</b> – Each {@code AD_Client_ID} has its own cache
 *       entry; different organisations in the same iDempiere installation may
 *       have different chart-of-account configurations.</li>
 *   <li><b>Graceful degradation</b> – If an account is not found in the
 *       database, the method returns {@code null} and logs a warning rather
 *       than throwing an exception.  Callers should check for {@code null} and
 *       handle the missing-account case appropriately.</li>
 *   <li><b>Thread safety</b> – The cache is backed by a
 *       {@link ConcurrentHashMap}; individual cache entries are immutable
 *       {@link Map} snapshots.</li>
 * </ol>
 *
 * <h3>Cache Invalidation</h3>
 * <p>Call {@link #invalidateCache(int)} after modifying chart-of-account data
 * for a specific tenant, or {@link #invalidateAllCaches()} to flush all
 * cached data.  The mapper will re-query the database on the next call.</p>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 * @see AccountingCodes
 * @see AccountingCodeMapperValidator
 */
public class AccountingCodeMapper {

    private static final Logger log = Logger.getLogger(AccountingCodeMapper.class.getName());

    // -----------------------------------------------------------------------
    // SQL query
    // -----------------------------------------------------------------------

    /**
     * Fetches all active leaf accounts (IsSummary='N') for a given tenant
     * and account-element combination, returning their {@code Value} and
     * {@code AccountType}.
     *
     * <p>The query joins {@code C_ElementValue} with {@code C_Element} to
     * obtain only accounts that belong to the <em>Account</em> element type
     * (ElementType='A'), which is the natural chart of accounts.</p>
     */
    private static final String SQL_FETCH_ACCOUNTS =
        "SELECT ev.Value, ev.Name, ev.AccountType, ev.C_ElementValue_ID "
        + "FROM C_ElementValue ev "
        + "JOIN C_Element e ON (e.C_Element_ID = ev.C_Element_ID) "
        + "WHERE ev.AD_Client_ID = ? "
        + "  AND ev.IsActive = 'Y' "
        + "  AND ev.IsSummary = 'N' "
        + "  AND e.ElementType = 'A' "
        + "ORDER BY ev.Value";

    // -----------------------------------------------------------------------
    // Cache
    // -----------------------------------------------------------------------

    /**
     * Two-level cache: {@code AD_Client_ID -> (accountValue -> AccountInfo)}.
     *
     * <p>Outer map uses {@code ConcurrentHashMap} for thread-safe per-tenant
     * access.  Inner maps are immutable snapshots that are replaced atomically
     * on cache refresh.</p>
     */
    private final ConcurrentHashMap<Integer, Map<String, AccountInfo>> cache =
        new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Inner type: AccountInfo
    // -----------------------------------------------------------------------

    /**
     * Lightweight value object returned by lookup methods.
     *
     * <p>Captures the information that callers need about a resolved account:
     * its primary key, display name, and account type.</p>
     */
    public static final class AccountInfo {

        /** The {@code C_ElementValue.Value} string (e.g. {@code "1112"}). */
        public final String value;

        /** The {@code C_ElementValue.Name} (Chinese description). */
        public final String name;

        /**
         * The iDempiere account type code:
         * {@code A}=Asset, {@code L}=Liability, {@code R}=Revenue, {@code E}=Expense.
         */
        public final String accountType;

        /** The {@code C_ElementValue_ID} primary key. */
        public final int elementValueId;

        AccountInfo(String value, String name, String accountType, int elementValueId) {
            this.value          = value;
            this.name           = name;
            this.accountType    = accountType;
            this.elementValueId = elementValueId;
        }

        @Override
        public String toString() {
            return "AccountInfo[" + value + " - " + name + " (" + accountType + ")]";
        }
    }

    // -----------------------------------------------------------------------
    // Public API – account lookup
    // -----------------------------------------------------------------------

    /**
     * Returns the Input Tax Receivable account (進項稅額) for the given tenant.
     *
     * <p>Default account code: {@link AccountingCodes#INPUT_TAX_ACCOUNT_VALUE}
     * ({@value AccountingCodes#INPUT_TAX_ACCOUNT_VALUE}).</p>
     *
     * @param clientId  iDempiere {@code AD_Client_ID}
     * @param ctx       iDempiere context (used for DB access)
     * @param trxName   transaction name, or {@code null}
     * @return {@link AccountInfo} for the resolved account, or {@code null} if not found
     */
    public AccountInfo getInputTaxAccount(int clientId, Properties ctx, String trxName) {
        return lookupAccount(clientId, AccountingCodes.INPUT_TAX_ACCOUNT_VALUE, ctx, trxName);
    }

    /**
     * Returns the Output Tax Payable account (銷項稅額) for the given tenant.
     *
     * <p>Default account code: {@link AccountingCodes#OUTPUT_TAX_ACCOUNT_VALUE}
     * ({@value AccountingCodes#OUTPUT_TAX_ACCOUNT_VALUE}).</p>
     *
     * @param clientId  iDempiere {@code AD_Client_ID}
     * @param ctx       iDempiere context
     * @param trxName   transaction name, or {@code null}
     * @return {@link AccountInfo} for the resolved account, or {@code null} if not found
     */
    public AccountInfo getOutputTaxAccount(int clientId, Properties ctx, String trxName) {
        return lookupAccount(clientId, AccountingCodes.OUTPUT_TAX_ACCOUNT_VALUE, ctx, trxName);
    }

    /**
     * Returns the Taxable Sales Revenue account (應稅銷售收入) for the given tenant.
     *
     * <p>Default account code: {@link AccountingCodes#TAXABLE_REVENUE_ACCOUNT_VALUE}
     * ({@value AccountingCodes#TAXABLE_REVENUE_ACCOUNT_VALUE}).</p>
     *
     * @param clientId  iDempiere {@code AD_Client_ID}
     * @param ctx       iDempiere context
     * @param trxName   transaction name, or {@code null}
     * @return {@link AccountInfo} for the resolved account, or {@code null} if not found
     */
    public AccountInfo getTaxableRevenueAccount(int clientId, Properties ctx, String trxName) {
        return lookupAccount(clientId, AccountingCodes.TAXABLE_REVENUE_ACCOUNT_VALUE, ctx, trxName);
    }

    /**
     * Returns the Tax-Exempt Sales Revenue account (免稅銷售收入) for the given tenant.
     *
     * <p>Default account code: {@link AccountingCodes#EXEMPT_REVENUE_ACCOUNT_VALUE}
     * ({@value AccountingCodes#EXEMPT_REVENUE_ACCOUNT_VALUE}).</p>
     *
     * @param clientId  iDempiere {@code AD_Client_ID}
     * @param ctx       iDempiere context
     * @param trxName   transaction name, or {@code null}
     * @return {@link AccountInfo} for the resolved account, or {@code null} if not found
     */
    public AccountInfo getExemptRevenueAccount(int clientId, Properties ctx, String trxName) {
        return lookupAccount(clientId, AccountingCodes.EXEMPT_REVENUE_ACCOUNT_VALUE, ctx, trxName);
    }

    /**
     * Returns the Cost of Goods Sold account (銷貨成本) for the given tenant.
     *
     * <p>Default account code: {@link AccountingCodes#COGS_ACCOUNT_VALUE}
     * ({@value AccountingCodes#COGS_ACCOUNT_VALUE}).</p>
     *
     * @param clientId  iDempiere {@code AD_Client_ID}
     * @param ctx       iDempiere context
     * @param trxName   transaction name, or {@code null}
     * @return {@link AccountInfo} for the resolved account, or {@code null} if not found
     */
    public AccountInfo getCogsAccount(int clientId, Properties ctx, String trxName) {
        return lookupAccount(clientId, AccountingCodes.COGS_ACCOUNT_VALUE, ctx, trxName);
    }

    /**
     * Verifies that the account identified by {@code accountValue} exists and
     * is active in the given tenant's chart of accounts.
     *
     * @param clientId     iDempiere {@code AD_Client_ID}
     * @param accountValue the {@code C_ElementValue.Value} to check (e.g. {@code "1112"})
     * @param ctx          iDempiere context
     * @param trxName      transaction name, or {@code null}
     * @return {@code true} if the account exists and is active; {@code false} otherwise
     */
    public boolean validateAccountExists(int clientId, String accountValue,
                                         Properties ctx, String trxName) {
        return lookupAccount(clientId, accountValue, ctx, trxName) != null;
    }

    /**
     * Looks up a specific account by its {@code Value} string within a tenant's
     * chart of accounts.
     *
     * <p>The cache is consulted first.  If no cache entry exists for
     * {@code clientId}, the full chart of accounts is fetched and stored before
     * returning the requested account.</p>
     *
     * @param clientId     {@code AD_Client_ID} of the tenant
     * @param accountValue the account code to look up (e.g. {@code "1112"})
     * @param ctx          iDempiere context
     * @param trxName      transaction name, or {@code null}
     * @return the matching {@link AccountInfo}, or {@code null} if not found
     */
    public AccountInfo lookupAccount(int clientId, String accountValue,
                                     Properties ctx, String trxName) {
        if (accountValue == null || accountValue.isEmpty()) {
            return null;
        }
        Map<String, AccountInfo> tenantCache = getOrLoadCache(clientId, ctx, trxName);
        AccountInfo info = tenantCache.get(accountValue);
        if (info == null) {
            log.log(Level.WARNING,
                "Account not found in C_ElementValue for AD_Client_ID={0}, Value={1}",
                new Object[]{clientId, accountValue});
        }
        return info;
    }

    // -----------------------------------------------------------------------
    // Cache management
    // -----------------------------------------------------------------------

    /**
     * Invalidates the cached chart-of-accounts data for the given tenant.
     *
     * <p>The next call to any lookup method will re-query the database.</p>
     *
     * @param clientId the {@code AD_Client_ID} whose cache should be cleared
     */
    public void invalidateCache(int clientId) {
        cache.remove(clientId);
        log.log(Level.FINE, "AccountingCodeMapper cache invalidated for AD_Client_ID={0}", clientId);
    }

    /**
     * Invalidates all cached chart-of-accounts data across all tenants.
     *
     * <p>Use this after bulk changes to account master data.</p>
     */
    public void invalidateAllCaches() {
        cache.clear();
        log.fine("AccountingCodeMapper cache fully invalidated");
    }

    /**
     * Returns the number of tenants currently held in the cache.
     *
     * @return number of cached tenant entries
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Returns an unmodifiable view of the cached accounts for a tenant, or an
     * empty map if the tenant has not been loaded yet.
     *
     * <p>Intended for testing and diagnostic purposes.</p>
     *
     * @param clientId the {@code AD_Client_ID}
     * @return unmodifiable map of {@code accountValue -> AccountInfo}
     */
    public Map<String, AccountInfo> getCachedAccountsForClient(int clientId) {
        Map<String, AccountInfo> tenantCache = cache.get(clientId);
        return (tenantCache != null) ? tenantCache : Collections.emptyMap();
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the cached account map for {@code clientId}, loading it from the
     * database if necessary.
     *
     * @param clientId {@code AD_Client_ID}
     * @param ctx      iDempiere context
     * @param trxName  transaction name
     * @return non-null, unmodifiable account map (may be empty if none found)
     */
    private Map<String, AccountInfo> getOrLoadCache(int clientId,
                                                     Properties ctx,
                                                     String trxName) {
        Map<String, AccountInfo> existing = cache.get(clientId);
        if (existing != null) {
            return existing;
        }
        Map<String, AccountInfo> loaded = loadAccountsFromDB(clientId, trxName);
        // putIfAbsent prevents duplicate loads in a race condition
        Map<String, AccountInfo> previous = cache.putIfAbsent(clientId, loaded);
        return (previous != null) ? previous : loaded;
    }

    /**
     * Queries {@code C_ElementValue} for all active leaf accounts belonging to
     * the given tenant and builds an immutable lookup map.
     *
     * <p>This method is {@code protected} to allow test subclasses to override
     * the database query with a stub implementation, keeping unit tests free
     * of any iDempiere runtime dependency.</p>
     *
     * @param clientId {@code AD_Client_ID}
     * @param trxName  transaction name
     * @return unmodifiable map keyed on {@code C_ElementValue.Value}
     */
    protected Map<String, AccountInfo> loadAccountsFromDB(int clientId, String trxName) {
        Map<String, AccountInfo> result = new HashMap<>();

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(SQL_FETCH_ACCOUNTS, trxName);
            pstmt.setInt(1, clientId);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                String value          = rs.getString("Value");
                String name           = rs.getString("Name");
                String accountType    = rs.getString("AccountType");
                int    elementValueId = rs.getInt("C_ElementValue_ID");

                result.put(value, new AccountInfo(value, name, accountType, elementValueId));
            }

            log.log(Level.FINE,
                "Loaded {0} accounts from C_ElementValue for AD_Client_ID={1}",
                new Object[]{result.size(), clientId});

        } catch (SQLException e) {
            log.log(Level.SEVERE,
                "Failed to load accounts from C_ElementValue for AD_Client_ID=" + clientId, e);
        } finally {
            DB.close(rs, pstmt);
        }

        return Collections.unmodifiableMap(result);
    }
}
