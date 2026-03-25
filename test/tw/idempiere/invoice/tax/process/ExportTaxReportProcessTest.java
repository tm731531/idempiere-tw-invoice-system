package tw.idempiere.invoice.tax.process;

import org.junit.Test;
import java.math.BigDecimal;
import static org.junit.Assert.*;

public class ExportTaxReportProcessTest {

    @Test
    public void testFormatCSVLine_allFields() {
        String line = ExportTaxReportProcess.formatCSVLine(
            2026, 1,
            new BigDecimal("1000000"),
            new BigDecimal("50000"),
            new BigDecimal("30000"),
            new BigDecimal("20000"));
        assertTrue(line.contains("2026"));
        assertTrue(line.contains("1000000"));
        assertTrue(line.contains("20000"));
    }

    @Test
    public void testFormatCSVLine_negativePayable_refund() {
        String line = ExportTaxReportProcess.formatCSVLine(
            2026, 1,
            new BigDecimal("100000"),
            new BigDecimal("5000"),
            new BigDecimal("10000"),
            new BigDecimal("-5000"));
        assertTrue(line.contains("-5000"));
    }

    @Test
    public void testCSVHeader_hasRequiredColumns() {
        String header = ExportTaxReportProcess.getCSVHeader();
        assertTrue(header.contains("TaxYear"));
        assertTrue(header.contains("OutputTax"));
        assertTrue(header.contains("InputTax"));
        assertTrue(header.contains("TaxPayable"));
    }
}
