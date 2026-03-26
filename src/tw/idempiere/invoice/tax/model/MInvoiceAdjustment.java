package tw.idempiere.invoice.tax.model;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Properties;

import org.adempiere.base.Model;
import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.model.POInfo;

/**
 * Taiwan Invoice Adjustment (進項折讓) — TW_InvoiceAdjustment
 *
 * Records sales (銷項折讓) and purchase (進項折讓) credit/debit adjustments.
 * AdjustmentDirection must distinguish SALES vs PURCHASE.
 * Late purchase adjustments (超期) carry tax penalty risk and require user confirmation.
 */
@Model(table = MInvoiceAdjustment.Table_Name)
public class MInvoiceAdjustment extends PO {

    private static final long serialVersionUID = 1L;

    public static final String Table_Name = "TW_InvoiceAdjustment";
    public static int Table_ID = 0;

    // Column name constants
    public static final String COLUMNNAME_TW_InvoiceAdjustment_ID = "TW_InvoiceAdjustment_ID";
    public static final String COLUMNNAME_C_Invoice_ID            = "C_Invoice_ID";
    public static final String COLUMNNAME_AdjustmentType          = "AdjustmentType";
    public static final String COLUMNNAME_AdjustmentDirection     = "AdjustmentDirection";
    public static final String COLUMNNAME_AdjustmentDate          = "AdjustmentDate";
    public static final String COLUMNNAME_AdjustedTaxAmount       = "AdjustedTaxAmount";
    public static final String COLUMNNAME_TaxPeriod               = "TaxPeriod";
    public static final String COLUMNNAME_IsOverduePeriod         = "IsOverduePeriod";
    public static final String COLUMNNAME_IsActive                = "IsActive";

    /** Adjustment direction: we issue a credit note to the buyer */
    public static final String ADJUSTMENTDIRECTION_Sales    = "SALES";
    /** Adjustment direction: supplier issues a credit note to us */
    public static final String ADJUSTMENTDIRECTION_Purchase = "PURCHASE";

    public MInvoiceAdjustment(Properties ctx, int TW_InvoiceAdjustment_ID, String trxName) {
        super(ctx, TW_InvoiceAdjustment_ID, trxName);
    }

    public MInvoiceAdjustment(Properties ctx, ResultSet rs, String trxName) {
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
        return "MInvoiceAdjustment[" + get_ID()
            + ", Direction=" + getAdjustmentDirection()
            + ", AdjustedTax=" + getAdjustedTaxAmount() + "]";
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public int getC_Invoice_ID() {
        Integer ii = (Integer) get_Value(COLUMNNAME_C_Invoice_ID);
        return ii == null ? 0 : ii;
    }

    public void setC_Invoice_ID(int C_Invoice_ID) {
        set_Value(COLUMNNAME_C_Invoice_ID, C_Invoice_ID);
    }

    public String getAdjustmentType() {
        return (String) get_Value(COLUMNNAME_AdjustmentType);
    }

    public void setAdjustmentType(String AdjustmentType) {
        set_Value(COLUMNNAME_AdjustmentType, AdjustmentType);
    }

    /**
     * AdjustmentDirection — "SALES" (銷項折讓) or "PURCHASE" (進項折讓).
     */
    public String getAdjustmentDirection() {
        return (String) get_Value(COLUMNNAME_AdjustmentDirection);
    }

    public void setAdjustmentDirection(String AdjustmentDirection) {
        set_Value(COLUMNNAME_AdjustmentDirection, AdjustmentDirection);
    }

    public java.sql.Timestamp getAdjustmentDate() {
        return (java.sql.Timestamp) get_Value(COLUMNNAME_AdjustmentDate);
    }

    public void setAdjustmentDate(java.sql.Timestamp AdjustmentDate) {
        set_Value(COLUMNNAME_AdjustmentDate, AdjustmentDate);
    }

    public BigDecimal getAdjustedTaxAmount() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_AdjustedTaxAmount);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setAdjustedTaxAmount(BigDecimal AdjustedTaxAmount) {
        set_Value(COLUMNNAME_AdjustedTaxAmount, AdjustedTaxAmount);
    }

    public String getTaxPeriod() {
        return (String) get_Value(COLUMNNAME_TaxPeriod);
    }

    public void setTaxPeriod(String TaxPeriod) {
        set_Value(COLUMNNAME_TaxPeriod, TaxPeriod);
    }

    public boolean isOverduePeriod() {
        Object oo = get_Value(COLUMNNAME_IsOverduePeriod);
        if (oo != null && oo instanceof Boolean)
            return (Boolean) oo;
        return "Y".equals(oo);
    }

    public void setIsOverduePeriod(boolean IsOverduePeriod) {
        set_Value(COLUMNNAME_IsOverduePeriod, IsOverduePeriod ? "Y" : "N");
    }

}
