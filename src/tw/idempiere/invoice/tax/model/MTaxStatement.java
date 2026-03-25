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
    public static final String COLUMNNAME_TW_TaxStatement_ID     = "TW_TaxStatement_ID";
    public static final String COLUMNNAME_TaxYear                = "TaxYear";
    public static final String COLUMNNAME_TaxPeriod              = "TaxPeriod";
    public static final String COLUMNNAME_StatementStatus        = "StatementStatus";
    public static final String COLUMNNAME_TaxableSalesAmount     = "TaxableSalesAmount";
    public static final String COLUMNNAME_ZeroRateSalesAmount    = "ZeroRateSalesAmount";
    public static final String COLUMNNAME_ExemptSalesAmount      = "ExemptSalesAmount";
    public static final String COLUMNNAME_OutputTax              = "OutputTax";
    public static final String COLUMNNAME_InputTax               = "InputTax";
    public static final String COLUMNNAME_NonDeductibleInputTax  = "NonDeductibleInputTax";
    public static final String COLUMNNAME_CarryOverTaxCredit     = "CarryOverTaxCredit";
    public static final String COLUMNNAME_MixedBusinessRatio     = "MixedBusinessRatio";
    public static final String COLUMNNAME_NetTaxPayable          = "NetTaxPayable";
    public static final String COLUMNNAME_IsActive               = "IsActive";

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
            + ", Period=" + getTaxYear() + "-" + getTaxPeriod()
            + ", Status=" + getStatementStatus() + "]";
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public int getTaxYear() {
        Integer ii = (Integer) get_Value(COLUMNNAME_TaxYear);
        return ii == null ? 0 : ii;
    }

    public void setTaxYear(int TaxYear) {
        set_Value(COLUMNNAME_TaxYear, TaxYear);
    }

    public int getTaxPeriod() {
        Integer ii = (Integer) get_Value(COLUMNNAME_TaxPeriod);
        return ii == null ? 0 : ii;
    }

    public void setTaxPeriod(int TaxPeriod) {
        set_Value(COLUMNNAME_TaxPeriod, TaxPeriod);
    }

    public String getStatementStatus() {
        return (String) get_Value(COLUMNNAME_StatementStatus);
    }

    public void setStatementStatus(String StatementStatus) {
        set_Value(COLUMNNAME_StatementStatus, StatementStatus);
    }

    public BigDecimal getTaxableSalesAmount() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_TaxableSalesAmount);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setTaxableSalesAmount(BigDecimal TaxableSalesAmount) {
        set_Value(COLUMNNAME_TaxableSalesAmount, TaxableSalesAmount);
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

    public BigDecimal getExemptSalesAmount() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_ExemptSalesAmount);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setExemptSalesAmount(BigDecimal ExemptSalesAmount) {
        set_Value(COLUMNNAME_ExemptSalesAmount, ExemptSalesAmount);
    }

    public BigDecimal getOutputTax() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_OutputTax);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setOutputTax(BigDecimal OutputTax) {
        set_Value(COLUMNNAME_OutputTax, OutputTax);
    }

    public BigDecimal getInputTax() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_InputTax);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setInputTax(BigDecimal InputTax) {
        set_Value(COLUMNNAME_InputTax, InputTax);
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

    public BigDecimal getMixedBusinessRatio() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_MixedBusinessRatio);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setMixedBusinessRatio(BigDecimal MixedBusinessRatio) {
        set_Value(COLUMNNAME_MixedBusinessRatio, MixedBusinessRatio);
    }

    public BigDecimal getNetTaxPayable() {
        BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_NetTaxPayable);
        return bd == null ? BigDecimal.ZERO : bd;
    }

    public void setNetTaxPayable(BigDecimal NetTaxPayable) {
        set_Value(COLUMNNAME_NetTaxPayable, NetTaxPayable);
    }

}
