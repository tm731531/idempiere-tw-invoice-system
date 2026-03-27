package tw.idempiere.invoice.tax.process;

import org.junit.Test;
import java.math.BigDecimal;
import static org.junit.Assert.*;
import static org.junit.Assert.assertThrows;

public class GenerateTaxStatementProcessTest {

    @Test
    public void testNetTaxPayable_outputMinusInput() {
        BigDecimal net = GenerateTaxStatementProcess.calcNetTaxPayable(
            new BigDecimal("50000"), new BigDecimal("30000"), BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(new BigDecimal("20000"), net);
    }

    @Test
    public void testNetTaxPayable_withCarryOver() {
        BigDecimal net = GenerateTaxStatementProcess.calcNetTaxPayable(
            new BigDecimal("50000"), new BigDecimal("30000"), BigDecimal.ZERO, new BigDecimal("5000"));
        assertEquals(new BigDecimal("15000"), net);
    }

    @Test
    public void testNetTaxPayable_negative_isRefund() {
        BigDecimal net = GenerateTaxStatementProcess.calcNetTaxPayable(
            new BigDecimal("10000"), new BigDecimal("30000"), BigDecimal.ZERO, BigDecimal.ZERO);
        assertTrue("Negative net tax = refund", net.compareTo(BigDecimal.ZERO) < 0);
    }

    @Test
    public void testNetTaxPayable_nonDeductibleReducesDeductible() {
        // outputTax=50000, adjustedInput=30000, nonDeductible=5000, carryOver=0
        // deductibleInput = 30000 - 5000 = 25000
        // net = 50000 - 25000 - 0 = 25000
        BigDecimal net = GenerateTaxStatementProcess.calcNetTaxPayable(
            new BigDecimal("50000"), new BigDecimal("30000"),
            new BigDecimal("5000"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("25000"), net);
    }

    @Test
    public void testPeriodYearFilter_correctRange() {
        // Period 1 = Jan-Feb, period 2 = Mar-Apr
        int[] months = GenerateTaxStatementProcess.getMonthsForPeriod(1);
        assertEquals(2, months.length);
        assertEquals(1, months[0]);  // January
        assertEquals(2, months[1]);  // February
    }

    @Test
    public void testPeriodYearFilter_period6() {
        int[] months = GenerateTaxStatementProcess.getMonthsForPeriod(6);
        assertEquals(11, months[0]);  // November
        assertEquals(12, months[1]);  // December
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetMonthsForPeriod_invalidPeriod_throws() {
        GenerateTaxStatementProcess.getMonthsForPeriod(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetMonthsForPeriod_periodTooHigh_throws() {
        GenerateTaxStatementProcess.getMonthsForPeriod(7);
    }

    @Test
    public void testDoIt_throwsUnsupportedOperationException() {
        GenerateTaxStatementProcess process = new GenerateTaxStatementProcess();
        assertThrows(UnsupportedOperationException.class, () -> process.doIt());
    }
}
