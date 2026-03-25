package tw.idempiere.invoice.tax.model;

import java.sql.ResultSet;
import java.util.Properties;

import org.adempiere.base.Model;
import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.model.POInfo;

/**
 * Taiwan Invoice Prefix (字軌管理) — TW_InvoicePrefix
 *
 * Manages invoice prefix allocations issued by the Ministry of Finance.
 * Each prefix covers a bimonthly period.
 */
@Model(table = MInvoicePrefix.Table_Name)
public class MInvoicePrefix extends PO {

    private static final long serialVersionUID = 1L;

    public static final String Table_Name = "TW_InvoicePrefix";
    public static int Table_ID = 0;

    // Column name constants
    public static final String COLUMNNAME_TW_InvoicePrefix_ID = "TW_InvoicePrefix_ID";
    public static final String COLUMNNAME_PrefixCode           = "PrefixCode";
    public static final String COLUMNNAME_PrefixStatus         = "PrefixStatus";
    public static final String COLUMNNAME_PrefixStartDate      = "PrefixStartDate";
    public static final String COLUMNNAME_PrefixEndDate        = "PrefixEndDate";
    public static final String COLUMNNAME_StartNumber          = "StartNumber";
    public static final String COLUMNNAME_EndNumber            = "EndNumber";
    public static final String COLUMNNAME_CurrentNumber        = "CurrentNumber";
    public static final String COLUMNNAME_IsActive             = "IsActive";

    public MInvoicePrefix(Properties ctx, int TW_InvoicePrefix_ID, String trxName) {
        super(ctx, TW_InvoicePrefix_ID, trxName);
    }

    public MInvoicePrefix(Properties ctx, ResultSet rs, String trxName) {
        super(ctx, rs, trxName);
    }

    @Override
    protected POInfo initPO(Properties ctx) {
        int tableId = MTable.getTable_ID(Table_Name);
        if (tableId <= 0) {
            // Table not yet installed via 2Pack — safe to return null before PackIn runs
            return null;
        }
        return POInfo.getPOInfo(ctx, tableId, get_TrxName());
    }

    @Override
    public int get_AccessLevel() {
        return 3;
    }

    @Override
    public String toString() {
        return "MInvoicePrefix[" + get_ID() + ", PrefixCode=" + getPrefixCode() + "]";
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getPrefixCode() {
        return (String) get_Value(COLUMNNAME_PrefixCode);
    }

    public void setPrefixCode(String PrefixCode) {
        set_Value(COLUMNNAME_PrefixCode, PrefixCode);
    }

    public String getPrefixStatus() {
        return (String) get_Value(COLUMNNAME_PrefixStatus);
    }

    public void setPrefixStatus(String PrefixStatus) {
        set_Value(COLUMNNAME_PrefixStatus, PrefixStatus);
    }

    public java.sql.Timestamp getPrefixStartDate() {
        return (java.sql.Timestamp) get_Value(COLUMNNAME_PrefixStartDate);
    }

    public void setPrefixStartDate(java.sql.Timestamp PrefixStartDate) {
        set_Value(COLUMNNAME_PrefixStartDate, PrefixStartDate);
    }

    public java.sql.Timestamp getPrefixEndDate() {
        return (java.sql.Timestamp) get_Value(COLUMNNAME_PrefixEndDate);
    }

    public void setPrefixEndDate(java.sql.Timestamp PrefixEndDate) {
        set_Value(COLUMNNAME_PrefixEndDate, PrefixEndDate);
    }

    public int getStartNumber() {
        Integer ii = (Integer) get_Value(COLUMNNAME_StartNumber);
        return ii == null ? 0 : ii;
    }

    public void setStartNumber(int StartNumber) {
        set_Value(COLUMNNAME_StartNumber, StartNumber);
    }

    public int getEndNumber() {
        Integer ii = (Integer) get_Value(COLUMNNAME_EndNumber);
        return ii == null ? 0 : ii;
    }

    public void setEndNumber(int EndNumber) {
        set_Value(COLUMNNAME_EndNumber, EndNumber);
    }

    public int getCurrentNumber() {
        Integer ii = (Integer) get_Value(COLUMNNAME_CurrentNumber);
        return ii == null ? 0 : ii;
    }

    public void setCurrentNumber(int CurrentNumber) {
        set_Value(COLUMNNAME_CurrentNumber, CurrentNumber);
    }

}
