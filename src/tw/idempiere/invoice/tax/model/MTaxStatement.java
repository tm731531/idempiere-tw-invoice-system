/******************************************************************************
 * Taiwan Invoice Tax System for iDempiere
 * Copyright (C) Taiwan iDempiere Community. All Rights Reserved.
 * License: GNU General Public License version 2
 *****************************************************************************/
package tw.idempiere.invoice.tax.model;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.logging.Level;

import org.compiere.model.PO;
import org.compiere.model.POInfo;

/**
 * Taiwan Tax Statement (稅務申報單) Model.
 *
 * <p>Stores the compiled VAT return (401 form) for each bimonthly reporting
 * period.  Supports both ordinary businesses and mixed-business operators
 * (兼營營業人) that must apportion input tax between taxable and exempt
 * activities.</p>
 *
 * <p>Table name: {@code TW_TaxStatement}</p>
 *
 * <h3>Period numbering</h3>
 * <pre>
 *   1 = Jan–Feb   2 = Mar–Apr   3 = May–Jun
 *   4 = Jul–Aug   5 = Sep–Oct   6 = Nov–Dec
 * </pre>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public class MTaxStatement extends PO {

    private static final long serialVersionUID = 1L;

    // -----------------------------------------------------------------------
    // Table / Column name constants
    // -----------------------------------------------------------------------

    /** Table name in the database */
    public static final String Table_Name = "TW_TaxStatement";

    /** Table ID – assigned when the table is registered in AD_Table */
    public static final int Table_ID = 0;

    /** Column: TW_TaxStatement_ID */
    public static final String COLUMNNAME_TW_TaxStatement_ID = "TW_TaxStatement_ID";

    /** Column: StatementPeriod */
    public static final String COLUMNNAME_StatementPeriod = "StatementPeriod";

    /** Column: StatementYear */
    public static final String COLUMNNAME_StatementYear = "StatementYear";

    /** Column: TaxableRevenue */
    public static final String COLUMNNAME_TaxableRevenue = "TaxableRevenue";

    /** Column: ExemptRevenue */
    public static final String COLUMNNAME_ExemptRevenue = "ExemptRevenue";

    /** Column: OutputTaxAmount */
    public static final String COLUMNNAME_OutputTaxAmount = "OutputTaxAmount";

    /** Column: InputTaxAmount */
    public static final String COLUMNNAME_InputTaxAmount = "InputTaxAmount";

    /** Column: TaxPayable */
    public static final String COLUMNNAME_TaxPayable = "TaxPayable";

    /** Column: IsMixedBusiness */
    public static final String COLUMNNAME_IsMixedBusiness = "IsMixedBusiness";

    /** Column: MixedBusinessRatio */
    public static final String COLUMNNAME_MixedBusinessRatio = "MixedBusinessRatio";

    // -----------------------------------------------------------------------
    // Access level
    // -----------------------------------------------------------------------

    /** Access level: Client + Org (3) */
    private static final int ACCESSLEVEL = 3;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Standard constructor – creates a new empty record when ID is 0,
     * or loads an existing record for the given ID.
     *
     * @param ctx                  iDempiere context
     * @param TW_TaxStatement_ID   record ID (0 for new record)
     * @param trxName              transaction name, or {@code null}
     */
    public MTaxStatement(Properties ctx, int TW_TaxStatement_ID, String trxName) {
        super(ctx, TW_TaxStatement_ID, trxName);
        if (TW_TaxStatement_ID == 0) {
            setInitialDefaults();
        }
    }

    /**
     * UUID-based constructor.
     *
     * @param ctx                  iDempiere context
     * @param TW_TaxStatement_UU   UUID of the record
     * @param trxName              transaction name, or {@code null}
     */
    public MTaxStatement(Properties ctx, String TW_TaxStatement_UU, String trxName) {
        super(ctx, TW_TaxStatement_UU, trxName);
        if (TW_TaxStatement_UU == null || TW_TaxStatement_UU.isEmpty()) {
            setInitialDefaults();
        }
    }

    /**
     * Load constructor – populates the model from a JDBC {@link ResultSet}.
     *
     * @param ctx      iDempiere context
     * @param rs       result set positioned at the row to load
     * @param trxName  transaction name, or {@code null}
     */
    public MTaxStatement(Properties ctx, ResultSet rs, String trxName) {
        super(ctx, rs, trxName);
    }

    /**
     * Sets sensible default values for a brand-new record.
     */
    private void setInitialDefaults() {
        setTaxableRevenue(BigDecimal.ZERO);
        setExemptRevenue(BigDecimal.ZERO);
        setOutputTaxAmount(BigDecimal.ZERO);
        setInputTaxAmount(BigDecimal.ZERO);
        setTaxPayable(BigDecimal.ZERO);
        setIsMixedBusiness(false);
    }

    // -----------------------------------------------------------------------
    // PO abstract method implementations
    // -----------------------------------------------------------------------

    @Override
    protected int get_AccessLevel() {
        return ACCESSLEVEL;
    }

    /**
     * Initialises PO column metadata from the iDempiere dictionary.
     *
     * <p>Returns {@code null} when the table has not yet been registered in
     * {@code AD_Table} (e.g., during development or unit tests).</p>
     *
     * @param ctx context
     * @return {@link POInfo} or {@code null}
     */
    @Override
    protected POInfo initPO(Properties ctx) {
        if (Table_ID <= 0) {
            if (log != null) {
                log.log(Level.WARNING,
                    "TW_TaxStatement table not yet registered in AD_Table dictionary. "
                    + "Register the table before deploying to production.");
            }
            return null;
        }
        return POInfo.getPOInfo(ctx, Table_ID, get_TrxName());
    }

    @Override
    public String toString() {
        return "MTaxStatement[" + get_ID()
            + ", Year=" + getStatementYear()
            + ", Period=" + getStatementPeriod()
            + "]";
    }

    // -----------------------------------------------------------------------
    // Validation – delegates to TaxStatementValidator (no iDempiere deps)
    // -----------------------------------------------------------------------

    /**
     * Lifecycle hook called by iDempiere before every save.
     *
     * <p>Validates:
     * <ol>
     *   <li>{@code StatementPeriod} must be 1–6.</li>
     *   <li>{@code StatementYear} must be &gt;= 2000.</li>
     *   <li>{@code TaxableRevenue} must be &gt;= 0.</li>
     *   <li>{@code ExemptRevenue} must be &gt;= 0.</li>
     *   <li>{@code OutputTaxAmount} must be &gt;= 0.</li>
     *   <li>{@code InputTaxAmount} must be &gt;= 0.</li>
     *   <li>Mixed-business ratio consistency.</li>
     * </ol>
     * </p>
     * <p>On success, {@code TaxPayable} is computed and stored.</p>
     *
     * @param newRecord {@code true} if this is an INSERT
     * @return {@code true} to allow the save; {@code false} to abort it
     */
    @Override
    protected boolean beforeSave(boolean newRecord) {
        TaxStatementValidator.ValidationResult result = TaxStatementValidator.validate(
            getStatementPeriod(),
            getStatementYear(),
            getTaxableRevenue(),
            getExemptRevenue(),
            getOutputTaxAmount(),
            getInputTaxAmount(),
            getIsMixedBusiness(),
            getMixedBusinessRatio());

        if (!result.valid) {
            if (log != null) {
                log.saveError("Error", result.errorMessage);
            }
            return false;
        }

        // Apply the computed TaxPayable
        if (result.resolvedTaxPayable != null
                && result.resolvedTaxPayable.compareTo(getTaxPayable()) != 0) {
            setTaxPayable(result.resolvedTaxPayable);
        }

        return true;
    }

    // -----------------------------------------------------------------------
    // Accessor methods
    // -----------------------------------------------------------------------

    /**
     * Sets the bimonthly statement period (1–6).
     *
     * @param StatementPeriod period number
     */
    public void setStatementPeriod(int StatementPeriod) {
        set_Value(COLUMNNAME_StatementPeriod, StatementPeriod);
    }

    /**
     * Returns the bimonthly statement period.
     *
     * @return period number (1–6), or {@code 0} if not set
     */
    public int getStatementPeriod() {
        Integer ii = (Integer) get_Value(COLUMNNAME_StatementPeriod);
        return (ii != null) ? ii.intValue() : 0;
    }

    /**
     * Sets the statement year.
     *
     * @param StatementYear calendar year (e.g., 2026)
     */
    public void setStatementYear(int StatementYear) {
        set_Value(COLUMNNAME_StatementYear, StatementYear);
    }

    /**
     * Returns the statement year.
     *
     * @return calendar year, or {@code 0} if not set
     */
    public int getStatementYear() {
        Integer ii = (Integer) get_Value(COLUMNNAME_StatementYear);
        return (ii != null) ? ii.intValue() : 0;
    }

    /**
     * Sets the taxable revenue (應稅銷售額).
     *
     * @param TaxableRevenue amount in TWD (&gt;= 0)
     */
    public void setTaxableRevenue(BigDecimal TaxableRevenue) {
        set_Value(COLUMNNAME_TaxableRevenue, TaxableRevenue);
    }

    /**
     * Returns the taxable revenue.
     *
     * @return amount in TWD, or {@link BigDecimal#ZERO} if not set
     */
    public BigDecimal getTaxableRevenue() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_TaxableRevenue);
        return (bd != null) ? bd : BigDecimal.ZERO;
    }

    /**
     * Sets the exempt revenue (免稅銷售額).
     *
     * @param ExemptRevenue amount in TWD (&gt;= 0)
     */
    public void setExemptRevenue(BigDecimal ExemptRevenue) {
        set_Value(COLUMNNAME_ExemptRevenue, ExemptRevenue);
    }

    /**
     * Returns the exempt revenue.
     *
     * @return amount in TWD, or {@link BigDecimal#ZERO} if not set
     */
    public BigDecimal getExemptRevenue() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_ExemptRevenue);
        return (bd != null) ? bd : BigDecimal.ZERO;
    }

    /**
     * Sets the output tax amount (銷項稅額).
     *
     * @param OutputTaxAmount VAT collected on sales, in TWD (&gt;= 0)
     */
    public void setOutputTaxAmount(BigDecimal OutputTaxAmount) {
        set_Value(COLUMNNAME_OutputTaxAmount, OutputTaxAmount);
    }

    /**
     * Returns the output tax amount.
     *
     * @return VAT collected on sales, or {@link BigDecimal#ZERO} if not set
     */
    public BigDecimal getOutputTaxAmount() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_OutputTaxAmount);
        return (bd != null) ? bd : BigDecimal.ZERO;
    }

    /**
     * Sets the input tax amount (進項稅額) before apportionment.
     *
     * @param InputTaxAmount VAT paid on purchases, in TWD (&gt;= 0)
     */
    public void setInputTaxAmount(BigDecimal InputTaxAmount) {
        set_Value(COLUMNNAME_InputTaxAmount, InputTaxAmount);
    }

    /**
     * Returns the input tax amount before apportionment.
     *
     * @return VAT paid on purchases, or {@link BigDecimal#ZERO} if not set
     */
    public BigDecimal getInputTaxAmount() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_InputTaxAmount);
        return (bd != null) ? bd : BigDecimal.ZERO;
    }

    /**
     * Sets the net VAT payable.
     *
     * <p>A negative value represents a refundable credit.
     * This value is normally computed by {@link #beforeSave(boolean)}.</p>
     *
     * @param TaxPayable net VAT due in TWD
     */
    public void setTaxPayable(BigDecimal TaxPayable) {
        set_Value(COLUMNNAME_TaxPayable, TaxPayable);
    }

    /**
     * Returns the net VAT payable.
     *
     * @return net VAT due (may be negative), or {@link BigDecimal#ZERO} if not set
     */
    public BigDecimal getTaxPayable() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_TaxPayable);
        return (bd != null) ? bd : BigDecimal.ZERO;
    }

    /**
     * Sets the mixed-business flag.
     *
     * @param isMixed {@code true} for 兼營營業人
     */
    public void setIsMixedBusiness(boolean isMixed) {
        set_Value(COLUMNNAME_IsMixedBusiness, isMixed ? "Y" : "N");
    }

    /**
     * Returns the mixed-business flag.
     *
     * @return {@code true} for a mixed-business operator
     */
    public boolean getIsMixedBusiness() {
        return "Y".equals(get_Value(COLUMNNAME_IsMixedBusiness));
    }

    /**
     * Sets the taxable-revenue apportionment ratio for mixed-business operators.
     *
     * @param MixedBusinessRatio ratio between 0.0000 and 1.0000;
     *                           {@code null} for ordinary businesses
     */
    public void setMixedBusinessRatio(BigDecimal MixedBusinessRatio) {
        set_Value(COLUMNNAME_MixedBusinessRatio, MixedBusinessRatio);
    }

    /**
     * Returns the taxable-revenue apportionment ratio.
     *
     * @return ratio (0–1), or {@code null} for ordinary businesses
     */
    public BigDecimal getMixedBusinessRatio() {
        return (BigDecimal) get_Value(COLUMNNAME_MixedBusinessRatio);
    }
}
