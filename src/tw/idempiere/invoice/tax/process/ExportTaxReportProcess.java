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
        return "TaxYear,TaxPeriod,TaxableRevenue,OutputTax,InputTax,TaxPayable";
    }

    public static String formatCSVLine(int taxYear, int taxPeriod,
                                        BigDecimal taxableRevenue,
                                        BigDecimal outputTax,
                                        BigDecimal inputTax,
                                        BigDecimal taxPayable) {
        return taxYear + "," + taxPeriod + "," +
               taxableRevenue.toPlainString() + "," +
               outputTax.toPlainString() + "," +
               inputTax.toPlainString() + "," +
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
        StringBuilder sb = new StringBuilder();
        sb.append(getCSVHeader()).append("\n");
        // TODO: query TW_TaxStatement for period, format each row
        return sb.toString();
    }
}
