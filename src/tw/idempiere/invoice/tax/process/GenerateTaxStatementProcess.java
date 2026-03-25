package tw.idempiere.invoice.tax.process;

import java.math.BigDecimal;
import java.util.logging.Logger;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import tw.idempiere.invoice.tax.service.MixedBusinessService;

public class GenerateTaxStatementProcess extends SvrProcess {

    private static final Logger log = Logger.getLogger(GenerateTaxStatementProcess.class.getName());

    private int p_TaxYear;
    private int p_TaxPeriod;

    // --- Static helper methods (unit-testable) ---

    public static BigDecimal calcNetTaxPayable(BigDecimal outputTax,
                                                BigDecimal adjustedInputTax,
                                                BigDecimal nonDeductibleInputTax,
                                                BigDecimal carryOverCredit) {
        // Per Taiwan VAT Act Art. 19:
        // deductibleInput = adjustedInputTax - nonDeductibleInputTax
        // netTaxPayable = outputTax - deductibleInput - carryOverCredit
        BigDecimal deductibleInput = adjustedInputTax.subtract(nonDeductibleInputTax);
        return outputTax.subtract(deductibleInput).subtract(carryOverCredit);
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
        // aggregate OutputTax, InputTax from TW_Invoice_Prefix_Map records for the period,
        // then create/update TW_TaxStatement record.

        // Calculation pipeline (values will be retrieved from MTaxStatement in Phase 4):
        // BigDecimal inputTax = ...; // from MTaxStatement.getInputTax()
        // BigDecimal ratio = ...; // from MTaxStatement.getMixedBusinessRatio()
        // BigDecimal adjustedInputTax = MixedBusinessService.adjustInputTax(inputTax, ratio);
        // BigDecimal nonDeductible = ...; // from MTaxStatement.getNonDeductibleInputTax()
        // BigDecimal carryOver = ...; // from MTaxStatement.getCarryOverTaxCredit()
        // BigDecimal output = ...; // from MTaxStatement.getOutputTax()
        // BigDecimal net = calcNetTaxPayable(output, adjustedInputTax, nonDeductible, carryOver);

        return "@TaxYear@=" + p_TaxYear + " @StatementPeriod@=" + p_TaxPeriod;
    }
}
