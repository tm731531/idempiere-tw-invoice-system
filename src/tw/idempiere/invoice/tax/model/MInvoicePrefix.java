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
 * Taiwan Invoice Prefix (字軌) Model.
 *
 * <p>Manages invoice prefix codes (e.g., AA, AB, ZZ) assigned by the Taiwan
 * Ministry of Finance. Each prefix represents a block of invoice numbers
 * that an organisation may issue within a bimonthly period.</p>
 *
 * <p>Table name: {@code TW_InvoicePrefix}</p>
 *
 * <h3>Status life cycle</h3>
 * <pre>
 *   Inactive (I) → Active (A) → Complete (C)
 * </pre>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public class MInvoicePrefix extends PO {

    private static final long serialVersionUID = 1L;

    // -----------------------------------------------------------------------
    // Table / Column name constants
    // -----------------------------------------------------------------------

    /** Table name in the database */
    public static final String Table_Name = "TW_InvoicePrefix";

    /** Table ID – assigned when the table is registered in AD_Table */
    public static final int Table_ID = 0;

    /** Column: TW_InvoicePrefix_ID */
    public static final String COLUMNNAME_TW_InvoicePrefix_ID = "TW_InvoicePrefix_ID";

    /** Column: PrefixCode */
    public static final String COLUMNNAME_PrefixCode = "PrefixCode";

    /** Column: InvoiceType */
    public static final String COLUMNNAME_InvoiceType = "InvoiceType";

    /** Column: CurrentNumber */
    public static final String COLUMNNAME_CurrentNumber = "CurrentNumber";

    /** Column: StartNumber */
    public static final String COLUMNNAME_StartNumber = "StartNumber";

    /** Column: EndNumber */
    public static final String COLUMNNAME_EndNumber = "EndNumber";

    /** Column: Status */
    public static final String COLUMNNAME_Status = "Status";

    /** Column: LastIssuedInvoiceDate */
    public static final String COLUMNNAME_LastIssuedInvoiceDate = "LastIssuedInvoiceDate";

    /** Column: LastInvoiceNumber */
    public static final String COLUMNNAME_LastInvoiceNumber = "LastInvoiceNumber";

    // -----------------------------------------------------------------------
    // InvoiceType constants
    // -----------------------------------------------------------------------

    /** Three-part (三聯式) invoice */
    public static final String INVOICETYPE_SALES_TRIPART = "SALES_TRIPART";

    /** Two-part (二聯式) invoice */
    public static final String INVOICETYPE_SALES_BIPART  = "SALES_BIPART";

    // -----------------------------------------------------------------------
    // Status constants
    // -----------------------------------------------------------------------

    /** Status: Active – currently in use */
    public static final String STATUS_Active   = "A";

    /** Status: Inactive – not yet started or suspended */
    public static final String STATUS_Inactive = "I";

    /** Status: Complete – all numbers in range have been issued */
    public static final String STATUS_Complete = "C";

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
     * @param ctx      iDempiere context (environment properties)
     * @param TW_InvoicePrefix_ID  record ID (0 for new record)
     * @param trxName  transaction name, or {@code null}
     */
    public MInvoicePrefix(Properties ctx, int TW_InvoicePrefix_ID, String trxName) {
        super(ctx, TW_InvoicePrefix_ID, trxName);
        if (TW_InvoicePrefix_ID == 0) {
            setInitialDefaults();
        }
    }

    /**
     * UUID-based constructor.
     *
     * @param ctx                  iDempiere context
     * @param TW_InvoicePrefix_UU  UUID of the record
     * @param trxName              transaction name, or {@code null}
     */
    public MInvoicePrefix(Properties ctx, String TW_InvoicePrefix_UU, String trxName) {
        super(ctx, TW_InvoicePrefix_UU, trxName);
        if (TW_InvoicePrefix_UU == null || TW_InvoicePrefix_UU.isEmpty()) {
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
    public MInvoicePrefix(Properties ctx, ResultSet rs, String trxName) {
        super(ctx, rs, trxName);
    }

    /**
     * Sets sensible default values for a brand-new record.
     */
    private void setInitialDefaults() {
        setStatus(STATUS_Active);
        setCurrentNumber(0);
        setStartNumber(1);
        setEndNumber(99999999);
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
     * <p>When the table has not yet been registered in {@code AD_Table} (e.g.,
     * during development or unit tests), this method returns {@code null} and
     * a warning is logged. The model will still compile and the accessor
     * methods will work via the PO value arrays once the table is registered.</p>
     *
     * @param ctx context
     * @return {@link POInfo} or {@code null} if the table is not yet in the dictionary
     */
    @Override
    protected POInfo initPO(Properties ctx) {
        if (Table_ID <= 0) {
            // Table not yet registered in AD_Table – acceptable during development
            if (log != null) {
                log.log(Level.WARNING,
                    "TW_InvoicePrefix table not yet registered in AD_Table dictionary. "
                    + "Register the table before deploying to production.");
            }
            return null;
        }
        return POInfo.getPOInfo(ctx, Table_ID, get_TrxName());
    }

    @Override
    public String toString() {
        return "MInvoicePrefix[" + get_ID() + ", PrefixCode=" + getPrefixCode() + "]";
    }

    // -----------------------------------------------------------------------
    // Validation – delegates to InvoicePrefixValidator (no iDempiere deps)
    // -----------------------------------------------------------------------

    /**
     * Convenience alias so callers can use {@code MInvoicePrefix.isValidPrefixCode(...)}.
     *
     * @param code the code to validate (may be {@code null})
     * @return {@code true} if the code is valid
     * @see InvoicePrefixValidator#isValidPrefixCode(String)
     */
    public static boolean isValidPrefixCode(String code) {
        return InvoicePrefixValidator.isValidPrefixCode(code);
    }

    /**
     * Convenience alias so callers can use {@code MInvoicePrefix.isValidNumberRange(...)}.
     *
     * @param startNumber start of range (must be {@literal >=} 1)
     * @param endNumber   end of range (must be {@literal >=} startNumber)
     * @return {@code true} if the range is valid
     * @see InvoicePrefixValidator#isValidNumberRange(int, int)
     */
    public static boolean isValidNumberRange(int startNumber, int endNumber) {
        return InvoicePrefixValidator.isValidNumberRange(startNumber, endNumber);
    }

    /**
     * Convenience alias for the full field validation.
     *
     * @param prefixCode    proposed prefix code
     * @param startNumber   proposed start number
     * @param endNumber     proposed end number
     * @param currentNumber proposed current number (0 = uninitialised)
     * @param newRecord     {@code true} for INSERT, {@code false} for UPDATE
     * @return a {@link InvoicePrefixValidator.ValidationResult}
     * @see InvoicePrefixValidator#validate(String, int, int, int, boolean)
     */
    public static InvoicePrefixValidator.ValidationResult validateFields(
            String prefixCode,
            int startNumber,
            int endNumber,
            int currentNumber,
            boolean newRecord) {
        return InvoicePrefixValidator.validate(prefixCode, startNumber, endNumber,
            currentNumber, newRecord);
    }

    /**
     * Lifecycle hook called by iDempiere before every save (insert and update).
     *
     * <p>Validates:
     * <ol>
     *   <li>{@code PrefixCode} must be exactly two uppercase ASCII letters.</li>
     *   <li>{@code StartNumber} must be {@literal >=} 1.</li>
     *   <li>{@code EndNumber} must be {@literal >=} {@code StartNumber}.</li>
     *   <li>If this is a new record and {@code CurrentNumber == 0},
     *       it is initialised to {@code StartNumber}.</li>
     * </ol>
     * </p>
     *
     * @param newRecord {@code true} if this is an INSERT, {@code false} for UPDATE
     * @return {@code true} to allow the save; {@code false} to abort it
     */
    @Override
    protected boolean beforeSave(boolean newRecord) {
        InvoicePrefixValidator.ValidationResult result = InvoicePrefixValidator.validate(
            getPrefixCode(),
            getStartNumber(),
            getEndNumber(),
            getCurrentNumber(),
            newRecord);

        if (!result.valid) {
            if (log != null) {
                log.saveError("Error", result.errorMessage);
            }
            return false;
        }

        // Apply initialised CurrentNumber if it changed (new record, was 0)
        if (result.resolvedCurrentNumber != getCurrentNumber()) {
            setCurrentNumber(result.resolvedCurrentNumber);
        }

        return true;
    }

    // -----------------------------------------------------------------------
    // Accessor methods
    // -----------------------------------------------------------------------

    /**
     * Sets the invoice prefix code.
     *
     * @param PrefixCode exactly 2 uppercase letters (e.g., "AA", "ZZ")
     */
    public void setPrefixCode(String PrefixCode) {
        set_Value(COLUMNNAME_PrefixCode, PrefixCode);
    }

    /**
     * Returns the invoice prefix code.
     *
     * @return 2-letter prefix code, or {@code null} if not set
     */
    public String getPrefixCode() {
        return (String) get_Value(COLUMNNAME_PrefixCode);
    }

    /**
     * Sets the invoice type.
     *
     * @param InvoiceType one of {@link #INVOICETYPE_SALES_TRIPART} or
     *                    {@link #INVOICETYPE_SALES_BIPART}
     */
    public void setInvoiceType(String InvoiceType) {
        set_Value(COLUMNNAME_InvoiceType, InvoiceType);
    }

    /**
     * Returns the invoice type.
     *
     * @return invoice type string, or {@code null} if not set
     */
    public String getInvoiceType() {
        return (String) get_Value(COLUMNNAME_InvoiceType);
    }

    /**
     * Sets the current (next-to-issue) invoice number.
     *
     * @param CurrentNumber current invoice number ({@code 0} indicates uninitialised)
     */
    public void setCurrentNumber(int CurrentNumber) {
        set_Value(COLUMNNAME_CurrentNumber, CurrentNumber);
    }

    /**
     * Returns the current invoice number.
     *
     * @return current number, or {@code 0} if not set / uninitialised
     */
    public int getCurrentNumber() {
        Integer ii = (Integer) get_Value(COLUMNNAME_CurrentNumber);
        return (ii != null) ? ii.intValue() : 0;
    }

    /**
     * Sets the first number in the invoice range.
     *
     * @param StartNumber start number (must be {@literal >=} 1)
     */
    public void setStartNumber(int StartNumber) {
        set_Value(COLUMNNAME_StartNumber, StartNumber);
    }

    /**
     * Returns the first number in the invoice range.
     *
     * @return start number
     */
    public int getStartNumber() {
        Integer ii = (Integer) get_Value(COLUMNNAME_StartNumber);
        return (ii != null) ? ii.intValue() : 0;
    }

    /**
     * Sets the last number in the invoice range.
     *
     * @param EndNumber end number (must be {@literal >=} {@link #getStartNumber()})
     */
    public void setEndNumber(int EndNumber) {
        set_Value(COLUMNNAME_EndNumber, EndNumber);
    }

    /**
     * Returns the last number in the invoice range.
     *
     * @return end number
     */
    public int getEndNumber() {
        Integer ii = (Integer) get_Value(COLUMNNAME_EndNumber);
        return (ii != null) ? ii.intValue() : 0;
    }

    /**
     * Sets the prefix block status.
     *
     * @param Status one of {@link #STATUS_Active}, {@link #STATUS_Inactive},
     *               or {@link #STATUS_Complete}
     */
    public void setStatus(String Status) {
        set_Value(COLUMNNAME_Status, Status);
    }

    /**
     * Returns the prefix block status.
     *
     * @return status code ("A", "I", or "C"), or {@code null} if not set
     */
    public String getStatus() {
        return (String) get_Value(COLUMNNAME_Status);
    }

    /**
     * Sets the date and time of the last invoice issued with this prefix.
     *
     * @param LastIssuedInvoiceDate timestamp of the last issued invoice
     */
    public void setLastIssuedInvoiceDate(Timestamp LastIssuedInvoiceDate) {
        set_Value(COLUMNNAME_LastIssuedInvoiceDate, LastIssuedInvoiceDate);
    }

    /**
     * Returns the date and time of the last invoice issued with this prefix.
     *
     * @return timestamp, or {@code null} if no invoice has been issued yet
     */
    public Timestamp getLastIssuedInvoiceDate() {
        return (Timestamp) get_Value(COLUMNNAME_LastIssuedInvoiceDate);
    }

    /**
     * Sets the number of the last invoice that was successfully issued.
     *
     * @param LastInvoiceNumber formatted invoice number (e.g., "AA00000001")
     */
    public void setLastInvoiceNumber(String LastInvoiceNumber) {
        set_Value(COLUMNNAME_LastInvoiceNumber, LastInvoiceNumber);
    }

    /**
     * Returns the number of the last invoice that was successfully issued.
     *
     * @return formatted invoice number, or {@code null} if none issued yet
     */
    public String getLastInvoiceNumber() {
        return (String) get_Value(COLUMNNAME_LastInvoiceNumber);
    }
}
