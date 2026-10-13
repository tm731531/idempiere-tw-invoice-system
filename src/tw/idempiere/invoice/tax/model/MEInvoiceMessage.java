package tw.idempiere.invoice.tax.model;

import java.sql.ResultSet;
import java.util.Properties;
import org.adempiere.base.Model;
import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.model.POInfo;

/** TW_EInvoiceMessage — one row per e-invoice transmission attempt. */
@Model(table = MEInvoiceMessage.Table_Name)
public class MEInvoiceMessage extends PO {
    private static final long serialVersionUID = 1L;
    public static final String Table_Name = "TW_EInvoiceMessage";
    public static int Table_ID = 0;

    public static final String COLUMNNAME_TW_EInvoiceMessage_ID = "TW_EInvoiceMessage_ID";
    public static final String COLUMNNAME_TW_EInvoiceMessage_UU = "TW_EInvoiceMessage_UU";
    public static final String COLUMNNAME_TW_Invoice_Prefix_Map_ID = "TW_Invoice_Prefix_Map_ID";
    public static final String COLUMNNAME_MessageType = "MessageType";
    public static final String COLUMNNAME_RandomNumber = "RandomNumber";
    public static final String COLUMNNAME_MigVersion = "MigVersion";
    public static final String COLUMNNAME_Status = "Status";
    public static final String COLUMNNAME_XmlFileName = "XmlFileName";
    public static final String COLUMNNAME_ErrorMessage = "ErrorMessage";
    public static final String COLUMNNAME_GeneratedOn = "GeneratedOn";

    public MEInvoiceMessage(Properties ctx, int id, String trxName) { super(ctx, id, trxName); }
    public MEInvoiceMessage(Properties ctx, ResultSet rs, String trxName) { super(ctx, rs, trxName); }

    @Override protected POInfo initPO(Properties ctx) {
        int tableId = MTable.getTable_ID(Table_Name);
        if (tableId <= 0) return null;
        return POInfo.getPOInfo(ctx, tableId, get_TrxName());
    }
    @Override public int get_AccessLevel() { return 3; }

    public int getTW_Invoice_Prefix_Map_ID() {
        Integer ii = (Integer) get_Value(COLUMNNAME_TW_Invoice_Prefix_Map_ID);
        return ii == null ? 0 : ii;
    }
    public void setTW_Invoice_Prefix_Map_ID(int id) { set_Value(COLUMNNAME_TW_Invoice_Prefix_Map_ID, id); }
    public String getMessageType() { return (String) get_Value(COLUMNNAME_MessageType); }
    public void setMessageType(String v) { set_Value(COLUMNNAME_MessageType, v); }
    public String getRandomNumber() { return (String) get_Value(COLUMNNAME_RandomNumber); }
    public void setRandomNumber(String v) { set_Value(COLUMNNAME_RandomNumber, v); }
    public String getMigVersion() { return (String) get_Value(COLUMNNAME_MigVersion); }
    public void setMigVersion(String v) { set_Value(COLUMNNAME_MigVersion, v); }
    public String getStatus() { return (String) get_Value(COLUMNNAME_Status); }
    public void setStatus(String v) { set_Value(COLUMNNAME_Status, v); }
    public String getXmlFileName() { return (String) get_Value(COLUMNNAME_XmlFileName); }
    public void setXmlFileName(String v) { set_Value(COLUMNNAME_XmlFileName, v); }
    public String getErrorMessage() { return (String) get_Value(COLUMNNAME_ErrorMessage); }
    public void setErrorMessage(String v) { set_Value(COLUMNNAME_ErrorMessage, v); }
}
