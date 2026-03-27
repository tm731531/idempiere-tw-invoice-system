package tw.idempiere.invoice.tax.process;

import java.math.BigDecimal;
import java.util.logging.Logger;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import tw.idempiere.invoice.tax.service.MixedBusinessService;

public class GenerateTaxStatementProcess extends SvrProcess {

    private static final Logger log = Logger.getLogger(GenerateTaxStatementProcess.class.getName());

    private int p_StatementYear;
    private int p_StatementPeriod;

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
        if (period < 1 || period > 6)
            throw new IllegalArgumentException("Tax period must be 1-6, got: " + period);
        int startMonth = (period - 1) * 2 + 1;
        return new int[]{startMonth, startMonth + 1};
    }

    // --- SvrProcess implementation ---

    @Override
    protected void prepare() {
        for (ProcessInfoParameter para : getParameter()) {
            if ("StatementYear".equals(para.getParameterName()))
                p_StatementYear = para.getParameterAsInt();
            else if ("StatementPeriod".equals(para.getParameterName()))
                p_StatementPeriod = para.getParameterAsInt();
        }
        if (p_StatementPeriod < 1 || p_StatementPeriod > 6)
            throw new IllegalArgumentException("StatementPeriod must be between 1 and 6");
    }

    @Override
    protected String doIt() throws Exception {
        log.info("GenerateTaxStatement: year=" + p_StatementYear + " period=" + p_StatementPeriod);

        // TODO: Phase 4 full implementation — query TW_Invoice_Prefix_Map,
        // aggregate OutputTaxAmount, InputTaxAmount from TW_Invoice_Prefix_Map records for the period,
        // then create/update TW_TaxStatement record.

        // Calculation pipeline (values will be retrieved from MTaxStatement in Phase 4):
        // BigDecimal inputTax = ...; // from MTaxStatement.getInputTaxAmount()
        // BigDecimal ratio = ...; // from MTaxStatement.getTaxableRatio()
        // BigDecimal adjustedInputTax = MixedBusinessService.adjustInputTax(inputTax, ratio);
        // BigDecimal nonDeductible = ...; // from MTaxStatement.getNonDeductibleInputTax()
        // BigDecimal carryOver = ...; // from MTaxStatement.getCarryOverTaxCredit()
        // BigDecimal output = ...; // from MTaxStatement.getOutputTaxAmount()
        // BigDecimal net = calcNetTaxPayable(output, adjustedInputTax, nonDeductible, carryOver);

        throw new UnsupportedOperationException(
            "GenerateTaxStatementProcess is not yet implemented. " +
            "Please use the TW_TaxStatement window to create statements manually.");
    }
}
