package tw.idempiere.invoice.tax.service;

import java.math.BigDecimal;
import java.time.Month;
import org.junit.Test;
import static org.junit.Assert.*;

public class TaxCalculationServiceTest {

    @Test
    public void testTripartOutputTax_floor() {
        // 100001 × 0.05 = 5000.05 → FLOOR → 5000 (not round to 5001)
        BigDecimal tax = TaxCalculationService.calcOutputTax(new BigDecimal("100001"));
        assertEquals(new BigDecimal("5000"), tax);
    }

    @Test
    public void testTripartGrossAmount() {
        BigDecimal gross = TaxCalculationService.calcGrossAmount(new BigDecimal("100000"));
        assertEquals(new BigDecimal("105000.00"), gross);
    }

    @Test
    public void testBipartReverseSaleAmount() {
        TaxCalculationService.TaxResult r =
            TaxCalculationService.calcFromGross(new BigDecimal("105000"));
        assertEquals(new BigDecimal("100000"), r.getSaleAmount());
        assertEquals(new BigDecimal("5000"), r.getTaxAmount());
    }

    @Test
    public void testBipartTaxAmount_usesFloorNotDifference() {
        // gross=105001, sale=floor(105001/1.05)=100000
        // tax = floor(100000 × 0.05) = 5000
        // NOT gross - sale = 105001 - 100000 = 5001 (WRONG — 1 yuan difference at boundary)
        TaxCalculationService.TaxResult r =
            TaxCalculationService.calcFromGross(new BigDecimal("105001"));
        assertEquals(new BigDecimal("100000"), r.getSaleAmount());
        assertEquals(new BigDecimal("5000"), r.getTaxAmount());
    }

    @Test
    public void testReportingPeriod_january() {
        assertEquals(1, TaxCalculationService.getReportingPeriod(Month.JANUARY));
    }

    @Test
    public void testReportingPeriod_february() {
        assertEquals(1, TaxCalculationService.getReportingPeriod(Month.FEBRUARY));
    }

    @Test
    public void testReportingPeriod_may() {
        assertEquals(3, TaxCalculationService.getReportingPeriod(Month.MAY));
    }

    @Test
    public void testReportingPeriod_december() {
        assertEquals(6, TaxCalculationService.getReportingPeriod(Month.DECEMBER));
    }

    @Test
    public void testNoBigDecimalRounding_usesFloor() {
        BigDecimal tax = TaxCalculationService.calcOutputTax(new BigDecimal("100"));
        assertEquals(new BigDecimal("5"), tax);
        BigDecimal tax2 = TaxCalculationService.calcOutputTax(new BigDecimal("101"));
        assertEquals(new BigDecimal("5"), tax2);
    }
}
