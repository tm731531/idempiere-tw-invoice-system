package tw.idempiere.invoice.tax.model;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Properties;

import org.adempiere.base.Model;
import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.model.POInfo;

/**
 * Taiwan Tax Statement (401 申報表) — TW_TaxStatement
 *
 * Bimonthly VAT declaration (Form 401) submitted to the Ministry of Finance.
 * Covers taxable sales, zero-rate exports, exempt sales, input tax credits,
 * mixed-business adjustments, and carry-over credits.
 */
@Model(table = MTaxStatement.Table_Name)
public class MTaxStatement extends PO {

    private static final long serialVersionUID = 1L;

    public static final String Table_Name = "TW_TaxStatement";
    public static int Table_ID = 0;

    // Column name constants
    public static final String COLUMNNAME_TW_TaxStatement_ID    = "TW_TaxStatement_ID";
    public static final String COLUMNNAME_TW_TaxStatement_UU    = "TW_TaxStatement_UU";
    public static final String COLUMNNAME_StatementYear         = "StatementYear";
    public static final String COLUMNNAME_StatementPeriod       = "StatementPeriod";
    public static final String COLUMNNAME_TaxableRevenue        = "TaxableRevenue";
    public static final String COLUMNNAME_ZeroRateSalesAmount   = "ZeroRateSalesAmount";
    public static final String COLUMNNAME_ExemptRevenue         = "ExemptRevenue";
    public static final String COLUMNNAME_OutputTaxAmount       = "OutputTaxAmount";
    public static final String COLUMNNAME_InputTaxAmount        = "InputTaxAmount";
    public static final String COLUMNNAME_NonDeductibleInputTax = "NonDeductibleInputTax";
    public static final String COLUMNNAME_CarryOverTaxCredit    = "CarryOverTaxCredit";
    public static final String COLUMNNAME_TaxableRatio          = "TaxableRatio";
    public static final String COLUMNNAME_AdjustedInputTax      = "AdjustedInputTax";
    public static final String COLUMNNAME_TaxPayable            = "TaxPayable";
    public static final String COLUMNNAME_IsMixedBusiness       = "IsMixedBusiness";
    public static final String COLUMNNAME_FilingDueDate         = "FilingDueDate";
    public static final String COLUMNNAME_IsActive              = "IsActive";

    public MTaxStatement(Properties ctx, int TW_TaxStatement_ID, String trxName) {
        super(ctx, TW_TaxStatement_ID, trxName);
    }

    public MTaxStatement(Properties ctx, ResultSet rs, String trxName) {
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
        return "MTaxStatement[" + get_ID()
            + ", Period=" + getStatementYear() + "-" + getStatementPeriod() + "]";
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public int getStatementYear() {
        Integer ii = (Integer) get_Value(COLUMNNAME_StatementYear);
        return ii == null ? 0 : ii;
    }

    public void setStatementYear(int StatementYear) {
        set_Value(COLUMNNAME_StatementYear, StatementYear);
    }

    public int getStatementPeriod() {
        Integer ii = (Integer) get_Value(COLUMNNAME_StatementPeriod);
        return ii == null ? 0 : ii;
    }

    public void setStatementPeriod(int StatementPeriod) {
        set_Value(COLUMNNAME_StatementPeriod, StatementPeriod);
    }

    public BigDecimal getTaxableRevenue() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_TaxableRevenue);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setTaxableRevenue(BigDecimal TaxableRevenue) {
        set_Value(COLUMNNAME_TaxableRevenue, TaxableRevenue);
    }

    /**
     * ZeroRateSalesAmount — zero-rate sales (出口), eligible for tax refund.
     * Must be reported separately from exempt sales.
     */
    public BigDecimal getZeroRateSalesAmount() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_ZeroRateSalesAmount);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setZeroRateSalesAmount(BigDecimal ZeroRateSalesAmount) {
        set_Value(COLUMNNAME_ZeroRateSalesAmount, ZeroRateSalesAmount);
    }

    public BigDecimal getExemptRevenue() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_ExemptRevenue);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setExemptRevenue(BigDecimal ExemptRevenue) {
        set_Value(COLUMNNAME_ExemptRevenue, ExemptRevenue);
    }

    public BigDecimal getOutputTaxAmount() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_OutputTaxAmount);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setOutputTaxAmount(BigDecimal OutputTaxAmount) {
        set_Value(COLUMNNAME_OutputTaxAmount, OutputTaxAmount);
    }

    public BigDecimal getInputTaxAmount() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_InputTaxAmount);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setInputTaxAmount(BigDecimal InputTaxAmount) {
        set_Value(COLUMNNAME_InputTaxAmount, InputTaxAmount);
    }

    /**
     * NonDeductibleInputTax — non-deductible input tax (不可扣抵進項稅額).
     */
    public BigDecimal getNonDeductibleInputTax() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_NonDeductibleInputTax);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setNonDeductibleInputTax(BigDecimal NonDeductibleInputTax) {
        set_Value(COLUMNNAME_NonDeductibleInputTax, NonDeductibleInputTax);
    }

    /**
     * CarryOverTaxCredit — cumulative carry-over credit from prior period (留抵稅額).
     */
    public BigDecimal getCarryOverTaxCredit() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_CarryOverTaxCredit);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setCarryOverTaxCredit(BigDecimal CarryOverTaxCredit) {
        set_Value(COLUMNNAME_CarryOverTaxCredit, CarryOverTaxCredit);
    }

    public BigDecimal getTaxableRatio() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_TaxableRatio);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setTaxableRatio(BigDecimal TaxableRatio) {
        set_Value(COLUMNNAME_TaxableRatio, TaxableRatio);
    }

    public BigDecimal getAdjustedInputTax() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_AdjustedInputTax);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setAdjustedInputTax(BigDecimal AdjustedInputTax) {
        set_Value(COLUMNNAME_AdjustedInputTax, AdjustedInputTax);
    }

    public BigDecimal getTaxPayable() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_TaxPayable);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setTaxPayable(BigDecimal TaxPayable) {
        set_Value(COLUMNNAME_TaxPayable, TaxPayable);
    }

    public boolean isMixedBusiness() {
        Object oo = get_Value(COLUMNNAME_IsMixedBusiness);
        if (oo != null && oo instanceof Boolean)
            return (Boolean) oo;
        return "Y".equals(oo);
    }

    public void setIsMixedBusiness(boolean IsMixedBusiness) {
        set_Value(COLUMNNAME_IsMixedBusiness, IsMixedBusiness ? "Y" : "N");
    }

    public java.sql.Timestamp getFilingDueDate() {
        return (java.sql.Timestamp) get_Value(COLUMNNAME_FilingDueDate);
    }

    public void setFilingDueDate(java.sql.Timestamp FilingDueDate) {
        set_Value(COLUMNNAME_FilingDueDate, FilingDueDate);
    }

}
