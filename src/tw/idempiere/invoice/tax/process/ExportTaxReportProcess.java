package tw.idempiere.invoice.tax.process;

import java.math.BigDecimal;
import java.util.logging.Logger;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;

public class ExportTaxReportProcess extends SvrProcess {

    private static final Logger log = Logger.getLogger(ExportTaxReportProcess.class.getName());

    private int p_TaxYear;
    private int p_TaxPeriod;

    public static String getCSVHeader() {
        return "TaxYear,TaxPeriod,TaxableRevenue,ZeroRateSalesAmount,ExemptSalesAmount," +
               "OutputTax,InputTax,NonDeductibleInputTax,CarryOverTaxCredit,TaxPayable";
    }

    public static String formatCSVLine(int taxYear, int taxPeriod,
                                        BigDecimal taxableRevenue,
                                        BigDecimal zeroRateSalesAmount,
                                        BigDecimal exemptSalesAmount,
                                        BigDecimal outputTax,
                                        BigDecimal inputTax,
                                        BigDecimal nonDeductibleInputTax,
                                        BigDecimal carryOverTaxCredit,
                                        BigDecimal taxPayable) {
        return taxYear + "," + taxPeriod + "," +
               taxableRevenue.toPlainString() + "," +
               zeroRateSalesAmount.toPlainString() + "," +
               exemptSalesAmount.toPlainString() + "," +
               outputTax.toPlainString() + "," +
               inputTax.toPlainString() + "," +
               nonDeductibleInputTax.toPlainString() + "," +
               carryOverTaxCredit.toPlainString() + "," +
               taxPayable.toPlainString();
    }

    @Override
    protected void prepare() {
        for (ProcessInfoParameter para : getParameter()) {
            if ("TaxYear".equals(para.getParameterName()))
                p_TaxYear = para.getParameterAsInt();
            else if ("TaxPeriod".equals(para.getParameterName()))
                p_TaxPeriod = para.getParameterAsInt();
        }
    }

    @Override
    protected String doIt() throws Exception {
        log.info("ExportTaxReport: year=" + p_TaxYear + " period=" + p_TaxPeriod);
        // TODO: Phase 4 full implementation — query TW_TaxStatement records for the period,
        // write CSV rows using formatCSVLine(), save to file or attachment, return record count.
        int exportedCount = 0;
        return "Exported " + exportedCount + " records successfully";
    }
}
