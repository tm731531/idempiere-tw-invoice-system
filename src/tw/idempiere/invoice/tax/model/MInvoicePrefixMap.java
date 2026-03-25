/******************************************************************************
 * Taiwan Invoice Tax System for iDempiere
 * Copyright (C) Taiwan iDempiere Community. All Rights Reserved.
 * License: GNU General Public License version 2
 *****************************************************************************/
package tw.idempiere.invoice.tax.model;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Properties;
import java.util.logging.Level;

import org.compiere.model.PO;
import org.compiere.model.POInfo;

/**
 * Taiwan Invoice Prefix Map Model.
 *
 * <p>Maps each {@code C_Invoice} record to its Taiwan invoice prefix (字軌) and
 * stores per-invoice validation flags: date-sequence integrity, month
 * consistency, and the 10-year input-tax expiry warning.</p>
 *
 * <p>Table name: {@code TW_Invoice_Prefix_Map}</p>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public class MInvoicePrefixMap extends PO {

    private static final long serialVersionUID = 1L;

    // -----------------------------------------------------------------------
    // Table / Column name constants
    // -----------------------------------------------------------------------

    /** Table name in the database */
    public static final String Table_Name = "TW_Invoice_Prefix_Map";

    /** Table ID – assigned when the table is registered in AD_Table */
    public static final int Table_ID = 0;

    /** Column: TW_InvoicePrefixMap_ID */
    public static final String COLUMNNAME_TW_InvoicePrefixMap_ID = "TW_InvoicePrefixMap_ID";

    /** Column: TW_InvoicePrefix_ID */
    public static final String COLUMNNAME_TW_InvoicePrefix_ID = "TW_InvoicePrefix_ID";

    /** Column: C_Invoice_ID */
    public static final String COLUMNNAME_C_Invoice_ID = "C_Invoice_ID";

    /** Column: InvoiceNumber */
    public static final String COLUMNNAME_InvoiceNumber = "InvoiceNumber";

    /** Column: DateInvoiced */
    public static final String COLUMNNAME_DateInvoiced = "DateInvoiced";

    /** Column: IsDateSequenceValid */
    public static final String COLUMNNAME_IsDateSequenceValid = "IsDateSequenceValid";

    /** Column: IsMonthConsistent */
    public static final String COLUMNNAME_IsMonthConsistent = "IsMonthConsistent";

    /** Column: MonthConsistentReason */
    public static final String COLUMNNAME_MonthConsistentReason = "MonthConsistentReason";

    /** Column: IsExpiryWarning */
    public static final String COLUMNNAME_IsExpiryWarning = "IsExpiryWarning";

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
     * @param ctx                      iDempiere context
     * @param TW_InvoicePrefixMap_ID   record ID (0 for new record)
     * @param trxName                  transaction name, or {@code null}
     */
    public MInvoicePrefixMap(Properties ctx, int TW_InvoicePrefixMap_ID, String trxName) {
        super(ctx, TW_InvoicePrefixMap_ID, trxName);
        if (TW_InvoicePrefixMap_ID == 0) {
            setInitialDefaults();
        }
    }

    /**
     * UUID-based constructor.
     *
     * @param ctx                       iDempiere context
     * @param TW_InvoicePrefixMap_UU    UUID of the record
     * @param trxName                   transaction name, or {@code null}
     */
    public MInvoicePrefixMap(Properties ctx, String TW_InvoicePrefixMap_UU, String trxName) {
        super(ctx, TW_InvoicePrefixMap_UU, trxName);
        if (TW_InvoicePrefixMap_UU == null || TW_InvoicePrefixMap_UU.isEmpty()) {
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
    public MInvoicePrefixMap(Properties ctx, ResultSet rs, String trxName) {
        super(ctx, rs, trxName);
    }

    /**
     * Sets sensible default values for a brand-new record.
     */
    private void setInitialDefaults() {
        setIsDateSequenceValid(true);
        setIsMonthConsistent(true);
        setIsExpiryWarning(false);
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
                    "TW_Invoice_Prefix_Map table not yet registered in AD_Table dictionary. "
                    + "Register the table before deploying to production.");
            }
            return null;
        }
        return POInfo.getPOInfo(ctx, Table_ID, get_TrxName());
    }

    @Override
    public String toString() {
        return "MInvoicePrefixMap[" + get_ID() + ", InvoiceNumber=" + getInvoiceNumber() + "]";
    }

    // -----------------------------------------------------------------------
    // Validation – delegates to InvoicePrefixMapValidator (no iDempiere deps)
    // -----------------------------------------------------------------------

    /**
     * Lifecycle hook called by iDempiere before every save.
     *
     * <p>Validates:
     * <ol>
     *   <li>{@code TW_InvoicePrefix_ID} must be &gt; 0.</li>
     *   <li>{@code C_Invoice_ID} must be &gt; 0.</li>
     *   <li>{@code InvoiceNumber} must match {@code [A-Z]{2}\d{7,8}}.</li>
     *   <li>{@code DateInvoiced} must not be {@code null}.</li>
     *   <li>{@code IsExpiryWarning} is computed from the invoice date.</li>
     * </ol>
     * </p>
     *
     * @param newRecord {@code true} if this is an INSERT
     * @return {@code true} to allow the save; {@code false} to abort it
     */
    @Override
    protected boolean beforeSave(boolean newRecord) {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        InvoicePrefixMapValidator.ValidationResult result = InvoicePrefixMapValidator.validate(
            getTW_InvoicePrefix_ID(),
            getC_Invoice_ID(),
            getInvoiceNumber(),
            getDateInvoiced(),
            now);

        if (!result.valid) {
            if (log != null) {
                log.saveError("Error", result.errorMessage);
            }
            return false;
        }

        // Apply computed expiry warning flag
        if (!result.resolvedIsExpiryWarning.equals(
                getIsExpiryWarning() ? "Y" : "N")) {
            setIsExpiryWarning("Y".equals(result.resolvedIsExpiryWarning));
        }

        return true;
    }

    // -----------------------------------------------------------------------
    // Accessor methods
    // -----------------------------------------------------------------------

    /**
     * Sets the foreign key to {@code TW_InvoicePrefix}.
     *
     * @param TW_InvoicePrefix_ID prefix record ID (must be &gt; 0)
     */
    public void setTW_InvoicePrefix_ID(int TW_InvoicePrefix_ID) {
        set_Value(COLUMNNAME_TW_InvoicePrefix_ID, TW_InvoicePrefix_ID);
    }

    /**
     * Returns the foreign key to {@code TW_InvoicePrefix}.
     *
     * @return prefix record ID, or {@code 0} if not set
     */
    public int getTW_InvoicePrefix_ID() {
        Integer ii = (Integer) get_Value(COLUMNNAME_TW_InvoicePrefix_ID);
        return (ii != null) ? ii.intValue() : 0;
    }

    /**
     * Sets the foreign key to {@code C_Invoice}.
     *
     * @param C_Invoice_ID invoice record ID (must be &gt; 0)
     */
    public void setC_Invoice_ID(int C_Invoice_ID) {
        set_Value(COLUMNNAME_C_Invoice_ID, C_Invoice_ID);
    }

    /**
     * Returns the foreign key to {@code C_Invoice}.
     *
     * @return invoice record ID, or {@code 0} if not set
     */
    public int getC_Invoice_ID() {
        Integer ii = (Integer) get_Value(COLUMNNAME_C_Invoice_ID);
        return (ii != null) ? ii.intValue() : 0;
    }

    /**
     * Sets the formatted Taiwan invoice number.
     *
     * @param InvoiceNumber e.g. {@code "AA0000001"}
     */
    public void setInvoiceNumber(String InvoiceNumber) {
        set_Value(COLUMNNAME_InvoiceNumber, InvoiceNumber);
    }

    /**
     * Returns the formatted Taiwan invoice number.
     *
     * @return invoice number, or {@code null} if not set
     */
    public String getInvoiceNumber() {
        return (String) get_Value(COLUMNNAME_InvoiceNumber);
    }

    /**
     * Sets the invoice issue date.
     *
     * @param DateInvoiced timestamp of issue
     */
    public void setDateInvoiced(Timestamp DateInvoiced) {
        set_Value(COLUMNNAME_DateInvoiced, DateInvoiced);
    }

    /**
     * Returns the invoice issue date.
     *
     * @return date invoiced, or {@code null} if not set
     */
    public Timestamp getDateInvoiced() {
        return (Timestamp) get_Value(COLUMNNAME_DateInvoiced);
    }

    /**
     * Sets whether the invoice date is in ascending sequence within its prefix.
     *
     * @param isValid {@code true} = valid sequence
     */
    public void setIsDateSequenceValid(boolean isValid) {
        set_Value(COLUMNNAME_IsDateSequenceValid, isValid ? "Y" : "N");
    }

    /**
     * Returns whether the invoice date is in ascending sequence within its prefix.
     *
     * @return {@code true} when the sequence is valid
     */
    public boolean getIsDateSequenceValid() {
        return "Y".equals(get_Value(COLUMNNAME_IsDateSequenceValid));
    }

    /**
     * Sets whether the invoice month is consistent with the transaction month.
     *
     * @param isConsistent {@code true} = consistent
     */
    public void setIsMonthConsistent(boolean isConsistent) {
        set_Value(COLUMNNAME_IsMonthConsistent, isConsistent ? "Y" : "N");
    }

    /**
     * Returns whether the invoice month is consistent with the transaction month.
     *
     * @return {@code true} when consistent
     */
    public boolean getIsMonthConsistent() {
        return "Y".equals(get_Value(COLUMNNAME_IsMonthConsistent));
    }

    /**
     * Sets the reason for allowing a cross-month exception.
     *
     * @param MonthConsistentReason explanation text
     */
    public void setMonthConsistentReason(String MonthConsistentReason) {
        set_Value(COLUMNNAME_MonthConsistentReason, MonthConsistentReason);
    }

    /**
     * Returns the cross-month exception reason.
     *
     * @return reason text, or {@code null} if not set
     */
    public String getMonthConsistentReason() {
        return (String) get_Value(COLUMNNAME_MonthConsistentReason);
    }

    /**
     * Sets the input-tax expiry warning flag.
     *
     * @param isWarning {@code true} when within 90 days of the 10-year expiry
     */
    public void setIsExpiryWarning(boolean isWarning) {
        set_Value(COLUMNNAME_IsExpiryWarning, isWarning ? "Y" : "N");
    }

    /**
     * Returns the input-tax expiry warning flag.
     *
     * @return {@code true} when the warning is active
     */
    public boolean getIsExpiryWarning() {
        return "Y".equals(get_Value(COLUMNNAME_IsExpiryWarning));
    }
}
