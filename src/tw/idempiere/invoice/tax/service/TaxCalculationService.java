package tw.idempiere.invoice.tax.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;

public class TaxCalculationService {

    private static final BigDecimal VAT_RATE = new BigDecimal("0.05");
    private static final BigDecimal ONE_PLUS_VAT = new BigDecimal("1.05");

    public static BigDecimal calcOutputTax(BigDecimal saleAmount) {
        return saleAmount.multiply(VAT_RATE).setScale(0, RoundingMode.FLOOR);
    }

    /**
     * Calculates gross amount for B2B (三聯式) invoices only.
     * Input is saleAmount (稅前金額); returns saleAmount + floor(saleAmount × 0.05).
     *
     * Do NOT pass a gross amount as input — this method treats its argument as
     * the pre-tax sale amount. For B2C reverse calculation, use calcSaleAmount().
     *
     * @param saleAmount pre-tax sale amount (稅前金額), must be non-negative
     * @return gross amount including 5% VAT
     */
    public static BigDecimal calcGrossAmount(BigDecimal saleAmount) {
        return saleAmount.add(calcOutputTax(saleAmount));
    }

    public static TaxResult calcFromGross(BigDecimal grossAmount) {
        BigDecimal saleAmount = grossAmount.divide(ONE_PLUS_VAT, 0, RoundingMode.FLOOR);
        BigDecimal taxAmount = saleAmount.multiply(VAT_RATE).setScale(0, RoundingMode.FLOOR);
        return new TaxResult(saleAmount, taxAmount);
    }

    public static int getReportingPeriod(Month month) {
        return (month.getValue() - 1) / 2 + 1;
    }

    public static class TaxResult {
        private final BigDecimal saleAmount;
        private final BigDecimal taxAmount;
        public TaxResult(BigDecimal saleAmount, BigDecimal taxAmount) {
            this.saleAmount = saleAmount;
            this.taxAmount = taxAmount;
        }
        public BigDecimal getSaleAmount() { return saleAmount; }
        public BigDecimal getTaxAmount() { return taxAmount; }
    }
}
