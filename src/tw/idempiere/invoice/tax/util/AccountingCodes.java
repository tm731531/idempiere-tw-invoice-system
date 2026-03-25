/******************************************************************************
 * Taiwan Invoice Tax System for iDempiere
 * Copyright (C) Taiwan iDempiere Community. All Rights Reserved.
 * License: GNU General Public License version 2
 *****************************************************************************/
package tw.idempiere.invoice.tax.util;

/**
 * Default accounting code constants for the Taiwan Invoice Tax System.
 *
 * <p>These constants represent the standard Taiwan Chart of Accounts
 * (ChartOfAccountsTW) account values defined in the iDempiere tenant
 * initialization data.  They serve as <em>fallback defaults</em>; the actual
 * account code used at runtime is always resolved dynamically by querying
 * {@code C_ElementValue} for the current tenant (AD_Client_ID / AD_Org_ID)
 * via {@link tw.idempiere.invoice.tax.service.AccountingCodeMapper}.</p>
 *
 * <p>Account code reference (ChartOfAccountsTW):
 * <ul>
 *   <li><b>1112</b> – 應收進項稅額 (Input Tax Receivable)</li>
 *   <li><b>2121</b> – 應付營業稅 (Output Tax Payable)</li>
 *   <li><b>4100</b> – 銷售收入-應稅 (Taxable Sales Revenue)</li>
 *   <li><b>4200</b> – 銷售收入-免稅 (Tax-Exempt Sales Revenue)</li>
 *   <li><b>5100</b> – 銷貨成本 (Cost of Goods Sold)</li>
 * </ul>
 * </p>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public final class AccountingCodes {

    /** Not instantiable – use the constants directly. */
    private AccountingCodes() {
    }

    // -----------------------------------------------------------------------
    // Default account value constants
    // -----------------------------------------------------------------------

    /**
     * Default value for the Input Tax Receivable account.
     * <p>Chinese: 應收進項稅額</p>
     * <p>Account type: Asset (借方 Debit)</p>
     * <p>Used to record VAT paid on purchases that can be offset against output tax.</p>
     */
    public static final String INPUT_TAX_ACCOUNT_VALUE = "1112";

    /**
     * Default value for the Output Tax Payable account.
     * <p>Chinese: 應付營業稅</p>
     * <p>Account type: Liability (貸方 Credit)</p>
     * <p>Used to record VAT collected on sales that must be remitted to the tax authority.</p>
     */
    public static final String OUTPUT_TAX_ACCOUNT_VALUE = "2121";

    /**
     * Default value for the Taxable Sales Revenue account.
     * <p>Chinese: 銷售收入-應稅</p>
     * <p>Account type: Revenue (收入)</p>
     * <p>Used to record sales subject to the standard 5% VAT rate.</p>
     */
    public static final String TAXABLE_REVENUE_ACCOUNT_VALUE = "4100";

    /**
     * Default value for the Tax-Exempt Sales Revenue account.
     * <p>Chinese: 銷售收入-免稅</p>
     * <p>Account type: Revenue (收入)</p>
     * <p>Used to record VAT-exempt sales (e.g. exported goods, certain medical services).</p>
     */
    public static final String EXEMPT_REVENUE_ACCOUNT_VALUE = "4200";

    /**
     * Default value for the Cost of Goods Sold account.
     * <p>Chinese: 銷貨成本</p>
     * <p>Account type: Expense (支出)</p>
     * <p>Used to record the cost of inventory sold during the period.</p>
     */
    public static final String COGS_ACCOUNT_VALUE = "5100";

    // -----------------------------------------------------------------------
    // Account type constants (mirrors C_ElementValue.AccountType)
    // -----------------------------------------------------------------------

    /** iDempiere account type code for Asset accounts (資產). */
    public static final String ACCOUNT_TYPE_ASSET     = "A";

    /** iDempiere account type code for Liability accounts (負債). */
    public static final String ACCOUNT_TYPE_LIABILITY = "L";

    /** iDempiere account type code for Revenue accounts (收入). */
    public static final String ACCOUNT_TYPE_REVENUE   = "R";

    /** iDempiere account type code for Expense accounts (支出). */
    public static final String ACCOUNT_TYPE_EXPENSE   = "E";

    // -----------------------------------------------------------------------
    // Expected account types for each default code
    // -----------------------------------------------------------------------

    /**
     * Returns the expected iDempiere {@code AccountType} for the given default
     * account code.
     *
     * @param accountValue one of the {@code *_ACCOUNT_VALUE} constants
     * @return the expected account type character, or {@code null} if
     *         {@code accountValue} is not recognised
     */
    public static String expectedAccountType(String accountValue) {
        if (accountValue == null) {
            return null;
        }
        switch (accountValue) {
            case INPUT_TAX_ACCOUNT_VALUE:      return ACCOUNT_TYPE_ASSET;
            case OUTPUT_TAX_ACCOUNT_VALUE:     return ACCOUNT_TYPE_LIABILITY;
            case TAXABLE_REVENUE_ACCOUNT_VALUE: return ACCOUNT_TYPE_REVENUE;
            case EXEMPT_REVENUE_ACCOUNT_VALUE:  return ACCOUNT_TYPE_REVENUE;
            case COGS_ACCOUNT_VALUE:            return ACCOUNT_TYPE_EXPENSE;
            default:                            return null;
        }
    }
}
