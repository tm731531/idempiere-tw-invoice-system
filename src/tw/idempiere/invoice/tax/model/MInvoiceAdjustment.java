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
 * Taiwan Invoice Adjustment (進項稅折讓) Model.
 *
 * <p>Records input-tax deduction adjustments (退回/折讓) linked to a previously
 * issued invoice via {@link MInvoicePrefixMap}.  Tracks whether the adjustment
 * was reported within the required bimonthly VAT filing period and raises an
 * overdue warning when the deadline is missed.</p>
 *
 * <p>Table name: {@code TW_InvoiceAdjustment}</p>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public class MInvoiceAdjustment extends PO {

    private static final long serialVersionUID = 1L;

    // -----------------------------------------------------------------------
    // Table / Column name constants
    // -----------------------------------------------------------------------

    /** Table name in the database */
    public static final String Table_Name = "TW_InvoiceAdjustment";

    /** Table ID – assigned when the table is registered in AD_Table */
    public static final int Table_ID = 0;

    /** Column: TW_InvoiceAdjustment_ID */
    public static final String COLUMNNAME_TW_InvoiceAdjustment_ID = "TW_InvoiceAdjustment_ID";

    /** Column: TW_InvoicePrefixMap_ID */
    public static final String COLUMNNAME_TW_InvoicePrefixMap_ID = "TW_InvoicePrefixMap_ID";

    /** Column: AdjustmentType */
    public static final String COLUMNNAME_AdjustmentType = "AdjustmentType";

    /** Column: AdjustmentAmount */
    public static final String COLUMNNAME_AdjustmentAmount = "AdjustmentAmount";

    /** Column: InputTaxAmount */
    public static final String COLUMNNAME_InputTaxAmount = "InputTaxAmount";

    /** Column: RequiredReportingPeriod */
    public static final String COLUMNNAME_RequiredReportingPeriod = "RequiredReportingPeriod";

    /** Column: IsReportedOnTime */
    public static final String COLUMNNAME_IsReportedOnTime = "IsReportedOnTime";

    /** Column: OverdueWarning */
    public static final String COLUMNNAME_OverdueWarning = "OverdueWarning";

    // -----------------------------------------------------------------------
    // AdjustmentType constants
    // -----------------------------------------------------------------------

    /** Adjustment type: goods return (退回) */
    public static final String ADJUSTMENTTYPE_RETURN    = "RETURN";

    /** Adjustment type: price allowance / debit note (折讓) */
    public static final String ADJUSTMENTTYPE_ALLOWANCE = "ALLOWANCE";

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
     * @param ctx                        iDempiere context
     * @param TW_InvoiceAdjustment_ID    record ID (0 for new record)
     * @param trxName                    transaction name, or {@code null}
     */
    public MInvoiceAdjustment(Properties ctx, int TW_InvoiceAdjustment_ID, String trxName) {
        super(ctx, TW_InvoiceAdjustment_ID, trxName);
        if (TW_InvoiceAdjustment_ID == 0) {
            setInitialDefaults();
        }
    }

    /**
     * UUID-based constructor.
     *
     * @param ctx                        iDempiere context
     * @param TW_InvoiceAdjustment_UU    UUID of the record
     * @param trxName                    transaction name, or {@code null}
     */
    public MInvoiceAdjustment(Properties ctx, String TW_InvoiceAdjustment_UU, String trxName) {
        super(ctx, TW_InvoiceAdjustment_UU, trxName);
        if (TW_InvoiceAdjustment_UU == null || TW_InvoiceAdjustment_UU.isEmpty()) {
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
    public MInvoiceAdjustment(Properties ctx, ResultSet rs, String trxName) {
        super(ctx, rs, trxName);
    }

    /**
     * Sets sensible default values for a brand-new record.
     */
    private void setInitialDefaults() {
        setInputTaxAmount(BigDecimal.ZERO);
        setIsReportedOnTime(true);
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
                    "TW_InvoiceAdjustment table not yet registered in AD_Table dictionary. "
                    + "Register the table before deploying to production.");
            }
            return null;
        }
        return POInfo.getPOInfo(ctx, Table_ID, get_TrxName());
    }

    @Override
    public String toString() {
        return "MInvoiceAdjustment[" + get_ID()
            + ", Type=" + getAdjustmentType()
            + ", Amount=" + getAdjustmentAmount()
            + "]";
    }

    // -----------------------------------------------------------------------
    // Validation – delegates to InvoiceAdjustmentValidator (no iDempiere deps)
    // -----------------------------------------------------------------------

    /**
     * Lifecycle hook called by iDempiere before every save.
     *
     * <p>Validates:
     * <ol>
     *   <li>{@code TW_InvoicePrefixMap_ID} must be &gt; 0.</li>
     *   <li>{@code AdjustmentType} must be {@code RETURN} or {@code ALLOWANCE}.</li>
     *   <li>{@code AdjustmentAmount} must be &gt; 0.</li>
     *   <li>{@code InputTaxAmount} must be &gt;= 0 and &lt;= {@code AdjustmentAmount}.</li>
     *   <li>{@code RequiredReportingPeriod} must be a valid bimonthly {@code YYYYMM} string.</li>
     * </ol>
     * </p>
     *
     * @param newRecord {@code true} if this is an INSERT
     * @return {@code true} to allow the save; {@code false} to abort it
     */
    @Override
    protected boolean beforeSave(boolean newRecord) {
        InvoiceAdjustmentValidator.ValidationResult result = InvoiceAdjustmentValidator.validate(
            getTW_InvoicePrefixMap_ID(),
            getAdjustmentType(),
            getAdjustmentAmount(),
            getInputTaxAmount(),
            getRequiredReportingPeriod());

        if (!result.valid) {
            if (log != null) {
                log.saveError("Error", result.errorMessage);
            }
            return false;
        }

        return true;
    }

    // -----------------------------------------------------------------------
    // Accessor methods
    // -----------------------------------------------------------------------

    /**
     * Sets the foreign key to {@code TW_Invoice_Prefix_Map}.
     *
     * @param TW_InvoicePrefixMap_ID map record ID (must be &gt; 0)
     */
    public void setTW_InvoicePrefixMap_ID(int TW_InvoicePrefixMap_ID) {
        set_Value(COLUMNNAME_TW_InvoicePrefixMap_ID, TW_InvoicePrefixMap_ID);
    }

    /**
     * Returns the foreign key to {@code TW_Invoice_Prefix_Map}.
     *
     * @return map record ID, or {@code 0} if not set
     */
    public int getTW_InvoicePrefixMap_ID() {
        Integer ii = (Integer) get_Value(COLUMNNAME_TW_InvoicePrefixMap_ID);
        return (ii != null) ? ii.intValue() : 0;
    }

    /**
     * Sets the adjustment type.
     *
     * @param AdjustmentType one of {@link #ADJUSTMENTTYPE_RETURN} or
     *                       {@link #ADJUSTMENTTYPE_ALLOWANCE}
     */
    public void setAdjustmentType(String AdjustmentType) {
        set_Value(COLUMNNAME_AdjustmentType, AdjustmentType);
    }

    /**
     * Returns the adjustment type.
     *
     * @return adjustment type string, or {@code null} if not set
     */
    public String getAdjustmentType() {
        return (String) get_Value(COLUMNNAME_AdjustmentType);
    }

    /**
     * Sets the total adjustment amount.
     *
     * @param AdjustmentAmount positive amount in TWD
     */
    public void setAdjustmentAmount(BigDecimal AdjustmentAmount) {
        set_Value(COLUMNNAME_AdjustmentAmount, AdjustmentAmount);
    }

    /**
     * Returns the total adjustment amount.
     *
     * @return adjustment amount, or {@link BigDecimal#ZERO} if not set
     */
    public BigDecimal getAdjustmentAmount() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_AdjustmentAmount);
        return (bd != null) ? bd : BigDecimal.ZERO;
    }

    /**
     * Sets the input-tax component of this adjustment.
     *
     * @param InputTaxAmount input tax amount in TWD (&gt;= 0)
     */
    public void setInputTaxAmount(BigDecimal InputTaxAmount) {
        set_Value(COLUMNNAME_InputTaxAmount, InputTaxAmount);
    }

    /**
     * Returns the input-tax component of this adjustment.
     *
     * @return input tax amount, or {@link BigDecimal#ZERO} if not set
     */
    public BigDecimal getInputTaxAmount() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_InputTaxAmount);
        return (bd != null) ? bd : BigDecimal.ZERO;
    }

    /**
     * Sets the required bimonthly reporting period.
     *
     * @param RequiredReportingPeriod {@code YYYYMM} string (month must be
     *                                01, 03, 05, 07, 09, or 11)
     */
    public void setRequiredReportingPeriod(String RequiredReportingPeriod) {
        set_Value(COLUMNNAME_RequiredReportingPeriod, RequiredReportingPeriod);
    }

    /**
     * Returns the required bimonthly reporting period.
     *
     * @return period string, or {@code null} if not set
     */
    public String getRequiredReportingPeriod() {
        return (String) get_Value(COLUMNNAME_RequiredReportingPeriod);
    }

    /**
     * Sets the on-time reporting flag.
     *
     * @param isReportedOnTime {@code true} when the adjustment was filed on time
     */
    public void setIsReportedOnTime(boolean isReportedOnTime) {
        set_Value(COLUMNNAME_IsReportedOnTime, isReportedOnTime ? "Y" : "N");
    }

    /**
     * Returns the on-time reporting flag.
     *
     * @return {@code true} when the adjustment was filed within the required period
     */
    public boolean getIsReportedOnTime() {
        return "Y".equals(get_Value(COLUMNNAME_IsReportedOnTime));
    }

    /**
     * Sets the overdue warning message.
     *
     * @param OverdueWarning human-readable warning text
     */
    public void setOverdueWarning(String OverdueWarning) {
        set_Value(COLUMNNAME_OverdueWarning, OverdueWarning);
    }

    /**
     * Returns the overdue warning message.
     *
     * @return warning text, or {@code null} if not set
     */
    public String getOverdueWarning() {
        return (String) get_Value(COLUMNNAME_OverdueWarning);
    }
}
