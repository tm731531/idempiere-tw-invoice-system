package tw.idempiere.invoice.tax.process;

import org.junit.Test;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Calendar;
import static org.junit.Assert.*;

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
    public void testCalcFilingDueDate_period1_isMarche15() {
        Timestamp due = GenerateTaxStatementProcess.calcFilingDueDate(2026, 1);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(due.getTime());
        assertEquals(2026, cal.get(Calendar.YEAR));
        assertEquals(Calendar.MARCH, cal.get(Calendar.MONTH));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void testCalcFilingDueDate_period6_isNextYearJan15() {
        // Period 6 = Nov-Dec; filing due = January 15 of the following year
        Timestamp due = GenerateTaxStatementProcess.calcFilingDueDate(2026, 6);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(due.getTime());
        assertEquals(2027, cal.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void testCalcFilingDueDate_allPeriods_correctMonth() {
        // period N ends at month N*2, filing due = month N*2+1, day 15
        int[][] expected = {
            {1, 3},  // period 1 (Jan-Feb) → March
            {2, 5},  // period 2 (Mar-Apr) → May
            {3, 7},  // period 3 (May-Jun) → July
            {4, 9},  // period 4 (Jul-Aug) → September
            {5, 11}, // period 5 (Sep-Oct) → November
        };
        for (int[] pair : expected) {
            Timestamp due = GenerateTaxStatementProcess.calcFilingDueDate(2026, pair[0]);
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(due.getTime());
            assertEquals("Period " + pair[0] + " filing month",
                pair[1], cal.get(Calendar.MONTH) + 1);
            assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
        }
    }
}
