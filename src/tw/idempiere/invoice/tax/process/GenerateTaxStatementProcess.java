package tw.idempiere.invoice.tax.process;

import java.math.BigDecimal;
import java.util.logging.Logger;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;

public class GenerateTaxStatementProcess extends SvrProcess {

    private static final Logger log = Logger.getLogger(GenerateTaxStatementProcess.class.getName());

    private int p_TaxYear;
    private int p_TaxPeriod;

    // --- Static helper methods (unit-testable) ---

    public static BigDecimal calcNetTaxPayable(BigDecimal outputTax,
                                                BigDecimal adjustedInputTax,
                                                BigDecimal carryOverCredit) {
        return outputTax.subtract(adjustedInputTax).subtract(carryOverCredit);
    }

    public static int[] getMonthsForPeriod(int period) {
        int startMonth = (period - 1) * 2 + 1;
        return new int[]{startMonth, startMonth + 1};
    }

    // --- SvrProcess implementation ---

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
        log.info("GenerateTaxStatement: year=" + p_TaxYear + " period=" + p_TaxPeriod);
        // TODO: Phase 4 full implementation — query TW_Invoice_Prefix_Map,
        // aggregate OutputTax, InputTax, apply MixedBusiness ratio, create TW_TaxStatement
        return "@TaxYear@=" + p_TaxYear + " @StatementPeriod@=" + p_TaxPeriod;
    }
}
