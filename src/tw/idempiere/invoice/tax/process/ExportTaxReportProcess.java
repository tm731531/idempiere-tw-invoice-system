package tw.idempiere.invoice.tax.process;

import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.logging.Logger;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.Env;

import tw.idempiere.invoice.tax.model.MTaxStatement;

public class ExportTaxReportProcess extends SvrProcess {

    private static final Logger log = Logger.getLogger(ExportTaxReportProcess.class.getName());

    private int p_StatementYear;
    private int p_StatementPeriod;

    // --- Static helper methods (unit-testable) ---

    public static String getCSVHeader() {
        return "StatementYear,StatementPeriod,TaxableRevenue,ZeroRateSalesAmount,ExemptRevenue," +
               "OutputTaxAmount,InputTaxAmount,NonDeductibleInputTax,CarryOverTaxCredit,TaxPayable";
    }

    public static String formatCSVLine(int statementYear, int statementPeriod,
                                        BigDecimal taxableRevenue,
                                        BigDecimal zeroRateSalesAmount,
                                        BigDecimal exemptRevenue,
                                        BigDecimal outputTaxAmount,
                                        BigDecimal inputTaxAmount,
                                        BigDecimal nonDeductibleInputTax,
                                        BigDecimal carryOverTaxCredit,
                                        BigDecimal taxPayable) {
        return statementYear + "," + statementPeriod + "," +
               taxableRevenue.toPlainString() + "," +
               zeroRateSalesAmount.toPlainString() + "," +
               exemptRevenue.toPlainString() + "," +
               outputTaxAmount.toPlainString() + "," +
               inputTaxAmount.toPlainString() + "," +
               nonDeductibleInputTax.toPlainString() + "," +
               carryOverTaxCredit.toPlainString() + "," +
               taxPayable.toPlainString();
    }

    @Override
    protected void prepare() {
        for (ProcessInfoParameter para : getParameter()) {
            if ("StatementYear".equals(para.getParameterName())) {
                p_StatementYear = para.getParameterAsInt();
            } else if ("StatementPeriod".equals(para.getParameterName())) {
                Object val = para.getParameter();
                if (val instanceof String)
                    p_StatementPeriod = Integer.parseInt(((String) val).trim());
                else if (val instanceof Number)
                    p_StatementPeriod = ((Number) val).intValue();
                else
                    p_StatementPeriod = para.getParameterAsInt();
            }
        }
    }

    @Override
    protected String doIt() throws Exception {
        int adClientId = Env.getAD_Client_ID(getCtx());

        log.info("ExportTaxReport: year=" + p_StatementYear + " period=" + p_StatementPeriod);

        // StatementPeriod is CHAR(1) in the physical table, must pass as String
        List<MTaxStatement> statements = new Query(getCtx(), MTaxStatement.Table_Name,
                "StatementYear=? AND StatementPeriod=? AND AD_Client_ID=?", get_TrxName())
            .setParameters(p_StatementYear, String.valueOf(p_StatementPeriod), adClientId)
            .list();

        if (statements.isEmpty()) {
            throw new AdempiereException(
                "No tax statement found for year " + p_StatementYear + " period " + p_StatementPeriod
                + ". Run GenerateTaxStatement process first.");
        }

        StringBuilder csv = new StringBuilder();
        csv.append(getCSVHeader()).append("\n");
        for (MTaxStatement s : statements) {
            csv.append(formatCSVLine(
                s.getStatementYear(), s.getStatementPeriod(),
                s.getTaxableRevenue(), s.getZeroRateSalesAmount(),
                s.getExemptRevenue(), s.getOutputTaxAmount(),
                s.getInputTaxAmount(), s.getNonDeductibleInputTax(),
                s.getCarryOverTaxCredit(), s.getTaxPayable()
            )).append("\n");
        }

        String fileName = "TW_TaxReport_" + p_StatementYear + "_P" + p_StatementPeriod + ".csv";
        String filePath = System.getProperty("java.io.tmpdir") + File.separator + fileName;

        try (FileWriter fw = new FileWriter(filePath)) {
            fw.write(csv.toString());
        }

        log.info("Tax report exported to: " + filePath);
        return "Exported " + statements.size() + " record(s) to: " + filePath;
    }
}
