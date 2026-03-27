package tw.idempiere.invoice.tax.process;

import org.junit.Test;
import java.math.BigDecimal;
import static org.junit.Assert.*;
import static org.junit.Assert.assertThrows;

public class ExportTaxReportProcessTest {

    @Test
    public void testFormatCSVLine_allFields() {
        String line = ExportTaxReportProcess.formatCSVLine(
            2026, 1,
            new BigDecimal("1000000"),
            new BigDecimal("0"),
            new BigDecimal("0"),
            new BigDecimal("50000"),
            new BigDecimal("30000"),
            new BigDecimal("2000"),
            new BigDecimal("3000"),
            new BigDecimal("15000"));
        assertTrue(line.contains("2026"));
        assertTrue(line.contains("1000000"));
        assertTrue(line.contains("15000"));
    }

    @Test
    public void testFormatCSVLine_negativePayable_refund() {
        String line = ExportTaxReportProcess.formatCSVLine(
            2026, 1,
            new BigDecimal("100000"),
            new BigDecimal("0"),
            new BigDecimal("0"),
            new BigDecimal("5000"),
            new BigDecimal("10000"),
            new BigDecimal("0"),
            new BigDecimal("0"),
            new BigDecimal("-5000"));
        assertTrue(line.contains("-5000"));
    }

    @Test
    public void testCSVHeader_hasRequiredColumns() {
        String header = ExportTaxReportProcess.getCSVHeader();
        assertTrue(header.contains("StatementYear"));
        assertTrue(header.contains("OutputTaxAmount"));
        assertTrue(header.contains("InputTaxAmount"));
        assertTrue(header.contains("TaxPayable"));
        assertTrue(header.contains("ZeroRateSalesAmount"));
        assertTrue(header.contains("ExemptRevenue"));
        assertTrue(header.contains("NonDeductibleInputTax"));
        assertTrue(header.contains("CarryOverTaxCredit"));
    }

    @Test
    public void testFormatCSVLine_zeroRateSales_included() {
        String line = ExportTaxReportProcess.formatCSVLine(
            2026, 2,
            new BigDecimal("500000"),
            new BigDecimal("100000"),
            new BigDecimal("50000"),
            new BigDecimal("20000"),
            new BigDecimal("10000"),
            new BigDecimal("1000"),
            new BigDecimal("2000"),
            new BigDecimal("7000"));
        assertTrue("Zero-rate amount should appear in CSV", line.contains("100000"));
        assertTrue("Exempt amount should appear in CSV", line.contains("50000"));
        assertTrue("NonDeductible should appear in CSV", line.contains("1000"));
        assertTrue("CarryOver should appear in CSV", line.contains("2000"));
    }

    @Test
    public void testDoIt_throwsUnsupportedOperationException() {
        ExportTaxReportProcess process = new ExportTaxReportProcess();
        assertThrows(UnsupportedOperationException.class, () -> process.doIt());
    }
}
