package tw.idempiere.invoice.tax.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MixedBusinessService {

    public static boolean isEligibleForAdjustment(int operatingMonths) {
        return operatingMonths >= 9;
    }

    public static BigDecimal calcTaxableRatio(BigDecimal taxableRevenue, BigDecimal totalRevenue) {
        if (totalRevenue == null || totalRevenue.compareTo(BigDecimal.ZERO) == 0)
            return BigDecimal.ZERO;
        return taxableRevenue.divide(totalRevenue, 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal adjustInputTax(BigDecimal inputTax, BigDecimal taxableRatio) {
        return inputTax.multiply(taxableRatio);
    }
}
