package tw.idempiere.invoice.tax.process;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Calendar;
import java.util.logging.Logger;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.compiere.util.Env;

import tw.idempiere.invoice.tax.model.MTaxStatement;

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

    /**
     * Filing due date = 15th of the month immediately after the period ends.
     * e.g. Period 1 (Jan-Feb) → March 15; Period 6 (Nov-Dec) → next-year January 15.
     */
    public static java.sql.Timestamp calcFilingDueDate(int year, int period) {
        int endMonth = period * 2;      // period 1 → 2 (Feb), period 6 → 12 (Dec)
        int filingMonth = endMonth + 1;
        int filingYear = year;
        if (filingMonth > 12) {
            filingMonth = 1;
            filingYear++;
        }
        Calendar cal = Calendar.getInstance();
        cal.set(filingYear, filingMonth - 1, 15, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new java.sql.Timestamp(cal.getTimeInMillis());
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
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
        int[] months = getMonthsForPeriod(p_StatementPeriod);
        int adClientId = Env.getAD_Client_ID(getCtx());

        log.info("GenerateTaxStatement: year=" + p_StatementYear + " period=" + p_StatementPeriod
            + " months=" + months[0] + "-" + months[1]);

        // Prevent duplicate statements for the same year+period
        MTaxStatement existing = new Query(getCtx(), MTaxStatement.Table_Name,
                "StatementYear=? AND StatementPeriod=? AND AD_Client_ID=?", get_TrxName())
            .setParameters(p_StatementYear, p_StatementPeriod, adClientId)
            .first();
        if (existing != null) {
            throw new AdempiereException(
                "Tax statement already exists for " + p_StatementYear + " period " + p_StatementPeriod
                + " (ID=" + existing.get_ID() + "). Delete it first or edit it directly in the TW_TaxStatement window.");
        }

        // --- Aggregate sales invoices: TaxableRevenue + OutputTaxAmount ---
        // TotalLines = sale amount before tax; GrandTotal - TotalLines = output tax
        BigDecimal taxableRevenue = BigDecimal.ZERO;
        BigDecimal outputTaxAmount = BigDecimal.ZERO;

        String salesSQL =
            "SELECT COALESCE(SUM(i.TotalLines), 0), COALESCE(SUM(i.GrandTotal - i.TotalLines), 0) " +
            "FROM TW_Invoice_Prefix_Map pm " +
            "JOIN C_Invoice i ON pm.C_Invoice_ID = i.C_Invoice_ID " +
            "WHERE pm.AD_Client_ID=? AND pm.C_Invoice_ID > 0 " +
            "AND pm.IsActive='Y' AND i.IsActive='Y' " +
            "AND EXTRACT(YEAR FROM pm.InvoiceDate)=? " +
            "AND EXTRACT(MONTH FROM pm.InvoiceDate) IN (?,?)";

        PreparedStatement pstmt = DB.prepareStatement(salesSQL, get_TrxName());
        ResultSet rs = null;
        try {
            pstmt.setInt(1, adClientId);
            pstmt.setInt(2, p_StatementYear);
            pstmt.setInt(3, months[0]);
            pstmt.setInt(4, months[1]);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                taxableRevenue = nullToZero(rs.getBigDecimal(1));
                outputTaxAmount = nullToZero(rs.getBigDecimal(2));
            }
        } finally {
            DB.close(rs, pstmt);
        }

        // --- Sales adjustments (銷項折讓) reduce output tax ---
        String salesAdjSQL =
            "SELECT COALESCE(SUM(AdjustedTaxAmount), 0) FROM TW_InvoiceAdjustment " +
            "WHERE AD_Client_ID=? AND IsActive='Y' " +
            "AND AdjustmentDirection='SALES' AND TaxPeriod=? " +
            "AND EXTRACT(YEAR FROM AdjustmentDate)=?";
        BigDecimal salesAdjTax = BigDecimal.ZERO;
        PreparedStatement pstmt2 = DB.prepareStatement(salesAdjSQL, get_TrxName());
        ResultSet rs2 = null;
        try {
            pstmt2.setInt(1, adClientId);
            pstmt2.setString(2, String.valueOf(p_StatementPeriod));
            pstmt2.setInt(3, p_StatementYear);
            rs2 = pstmt2.executeQuery();
            if (rs2.next()) salesAdjTax = nullToZero(rs2.getBigDecimal(1));
        } finally {
            DB.close(rs2, pstmt2);
        }
        outputTaxAmount = outputTaxAmount.subtract(salesAdjTax);

        // --- Purchase adjustments (進項折讓) = deductible input tax ---
        String purchAdjSQL =
            "SELECT COALESCE(SUM(AdjustedTaxAmount), 0) FROM TW_InvoiceAdjustment " +
            "WHERE AD_Client_ID=? AND IsActive='Y' " +
            "AND AdjustmentDirection='PURCHASE' AND TaxPeriod=? " +
            "AND EXTRACT(YEAR FROM AdjustmentDate)=?";
        BigDecimal inputTaxAmount = BigDecimal.ZERO;
        PreparedStatement pstmt3 = DB.prepareStatement(purchAdjSQL, get_TrxName());
        ResultSet rs3 = null;
        try {
            pstmt3.setInt(1, adClientId);
            pstmt3.setString(2, String.valueOf(p_StatementPeriod));
            pstmt3.setInt(3, p_StatementYear);
            rs3 = pstmt3.executeQuery();
            if (rs3.next()) inputTaxAmount = nullToZero(rs3.getBigDecimal(1));
        } finally {
            DB.close(rs3, pstmt3);
        }

        // Apply FLOOR rounding per Ministry of Finance rules
        outputTaxAmount = outputTaxAmount.setScale(0, RoundingMode.FLOOR);
        inputTaxAmount  = inputTaxAmount.setScale(0, RoundingMode.FLOOR);
        taxableRevenue  = taxableRevenue.setScale(2, RoundingMode.FLOOR);

        // Initial TaxPayable — user must review and fill in NonDeductibleInputTax,
        // CarryOverTaxCredit, and IsMixedBusiness before finalizing.
        BigDecimal taxPayable = calcNetTaxPayable(
            outputTaxAmount, inputTaxAmount, BigDecimal.ZERO, BigDecimal.ZERO);

        // --- Create TW_TaxStatement record ---
        MTaxStatement stmt = new MTaxStatement(getCtx(), 0, get_TrxName());
        stmt.setStatementYear(p_StatementYear);
        stmt.setStatementPeriod(p_StatementPeriod);
        stmt.setTaxableRevenue(taxableRevenue);
        stmt.setZeroRateSalesAmount(BigDecimal.ZERO);   // user fills in zero-rate (export) sales
        stmt.setExemptRevenue(BigDecimal.ZERO);          // user fills in exempt sales
        stmt.setOutputTaxAmount(outputTaxAmount);
        stmt.setInputTaxAmount(inputTaxAmount);
        stmt.setNonDeductibleInputTax(BigDecimal.ZERO);  // user fills in
        stmt.setCarryOverTaxCredit(BigDecimal.ZERO);     // user fills in from prior period
        stmt.setIsMixedBusiness(false);
        stmt.setTaxPayable(taxPayable);
        stmt.setFilingDueDate(calcFilingDueDate(p_StatementYear, p_StatementPeriod));

        if (!stmt.save())
            throw new AdempiereException("Failed to save TW_TaxStatement");

        log.info("TW_TaxStatement created: ID=" + stmt.get_ID());

        return "Tax statement generated (ID=" + stmt.get_ID() + "): "
            + "TaxableRevenue=" + taxableRevenue
            + ", OutputTax=" + outputTaxAmount
            + ", InputTax=" + inputTaxAmount
            + ", TaxPayable=" + taxPayable
            + ". Open TW_TaxStatement window to complete ZeroRateSalesAmount, "
            + "ExemptRevenue, NonDeductibleInputTax, CarryOverTaxCredit, and IsMixedBusiness.";
    }
}
