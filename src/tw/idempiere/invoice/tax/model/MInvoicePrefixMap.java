package tw.idempiere.invoice.tax.model;

import java.sql.ResultSet;
import java.util.Properties;

import org.adempiere.base.Model;
import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.model.POInfo;

/**
 * Taiwan Invoice Prefix Map (發票字軌對應) — TW_Invoice_Prefix_Map
 *
 * Maps issued invoices to their prefix allocations, including buyer tax ID
 * for B2B (三聯式) invoices.
 */
@Model(table = MInvoicePrefixMap.Table_Name)
public class MInvoicePrefixMap extends PO {

    private static final long serialVersionUID = 1L;

    public static final String Table_Name = "TW_Invoice_Prefix_Map";
    public static int Table_ID = 0;

    // Column name constants
    public static final String COLUMNNAME_TW_Invoice_Prefix_Map_ID = "TW_Invoice_Prefix_Map_ID";
    public static final String COLUMNNAME_TW_InvoicePrefix_ID      = "TW_InvoicePrefix_ID";
    public static final String COLUMNNAME_InvoiceNumber            = "InvoiceNumber";
    public static final String COLUMNNAME_BuyerTaxID               = "BuyerTaxID";
    public static final String COLUMNNAME_InvoiceType              = "InvoiceType";
    public static final String COLUMNNAME_DateInvoiced             = "DateInvoiced";
    public static final String COLUMNNAME_IsActive                 = "IsActive";

    public MInvoicePrefixMap(Properties ctx, int TW_Invoice_Prefix_Map_ID, String trxName) {
        super(ctx, TW_Invoice_Prefix_Map_ID, trxName);
    }

    public MInvoicePrefixMap(Properties ctx, ResultSet rs, String trxName) {
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
        return "MInvoicePrefixMap[" + get_ID() + ", InvoiceNumber=" + getInvoiceNumber() + "]";
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public int getTW_InvoicePrefix_ID() {
        Integer ii = (Integer) get_Value(COLUMNNAME_TW_InvoicePrefix_ID);
        return ii == null ? 0 : ii;
    }

    public void setTW_InvoicePrefix_ID(int TW_InvoicePrefix_ID) {
        set_Value(COLUMNNAME_TW_InvoicePrefix_ID, TW_InvoicePrefix_ID);
    }

    public String getInvoiceNumber() {
        return (String) get_Value(COLUMNNAME_InvoiceNumber);
    }

    public void setInvoiceNumber(String InvoiceNumber) {
        set_Value(COLUMNNAME_InvoiceNumber, InvoiceNumber);
    }

    /**
     * BuyerTaxID — CHAR(8), mandatory for B2B (三聯式 / SALES_TRIPART) invoices.
     */
    public String getBuyerTaxID() {
        return (String) get_Value(COLUMNNAME_BuyerTaxID);
    }

    public void setBuyerTaxID(String BuyerTaxID) {
        set_Value(COLUMNNAME_BuyerTaxID, BuyerTaxID);
    }

    public String getInvoiceType() {
        return (String) get_Value(COLUMNNAME_InvoiceType);
    }

    public void setInvoiceType(String InvoiceType) {
        set_Value(COLUMNNAME_InvoiceType, InvoiceType);
    }

    public java.sql.Timestamp getDateInvoiced() {
        return (java.sql.Timestamp) get_Value(COLUMNNAME_DateInvoiced);
    }

    public void setDateInvoiced(java.sql.Timestamp DateInvoiced) {
        set_Value(COLUMNNAME_DateInvoiced, DateInvoiced);
    }

}
