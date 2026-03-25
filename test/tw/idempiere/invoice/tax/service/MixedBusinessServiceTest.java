package tw.idempiere.invoice.tax.service;

import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.*;

public class MixedBusinessServiceTest {

    @Test
    public void testLessThan9Months_noAdjustmentNeeded() {
        assertFalse(MixedBusinessService.isEligibleForAdjustment(8));
    }

    @Test
    public void testExactly9Months_adjustmentNeeded() {
        assertTrue(MixedBusinessService.isEligibleForAdjustment(9));
    }

    @Test
    public void testMoreThan9Months_adjustmentNeeded() {
        assertTrue(MixedBusinessService.isEligibleForAdjustment(12));
    }

    @Test
    public void testTaxableRatio_80percent() {
        BigDecimal ratio = MixedBusinessService.calcTaxableRatio(
            new BigDecimal("800000"), new BigDecimal("1000000"));
        assertEquals(new BigDecimal("0.8000"), ratio);
    }

    @Test
    public void testTaxableRatio_fullTaxable() {
        BigDecimal ratio = MixedBusinessService.calcTaxableRatio(
            new BigDecimal("1000000"), new BigDecimal("1000000"));
        assertEquals(new BigDecimal("1.0000"), ratio);
    }

    @Test
    public void testAdjustInputTax() {
        BigDecimal adjusted = MixedBusinessService.adjustInputTax(
            new BigDecimal("100000"), new BigDecimal("0.8000"));
        assertEquals(new BigDecimal("80000"), adjusted);
    }

    @Test
    public void testAdjustInputTax_floorRounding() {
        // 100000 * 0.6666 = 66660.0000 → FLOOR → 66660 (not 66661)
        BigDecimal result = MixedBusinessService.adjustInputTax(
            new BigDecimal("100000"), new BigDecimal("0.6666"));
        assertEquals(new BigDecimal("66660"), result);
    }

    @Test
    public void testZeroTotalRevenue_returnsZeroRatio() {
        BigDecimal ratio = MixedBusinessService.calcTaxableRatio(
            BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO, ratio);
    }

    @Test
    public void testTaxableRatio_usesFloorNotHalfUp() {
        // 2/3 = 0.6666... → FLOOR → 0.6666 (not HALF_UP → 0.6667)
        BigDecimal ratio = MixedBusinessService.calcTaxableRatio(
            new BigDecimal("2"), new BigDecimal("3"));
        assertEquals(new BigDecimal("0.6666"), ratio);
    }
}
