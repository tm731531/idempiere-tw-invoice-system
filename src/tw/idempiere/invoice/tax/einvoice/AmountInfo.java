package tw.idempiere.invoice.tax.einvoice;

import java.math.BigDecimal;

/** F0401 Amount block. */
public final class AmountInfo {
    public final BigDecimal salesAmount, freeTaxSalesAmount, zeroTaxSalesAmount;
    public final String taxType;
    public final BigDecimal taxRate, taxAmount, totalAmount;

    public AmountInfo(BigDecimal salesAmount, BigDecimal freeTaxSalesAmount, BigDecimal zeroTaxSalesAmount,
                       String taxType, BigDecimal taxRate, BigDecimal taxAmount, BigDecimal totalAmount) {
        this.salesAmount = salesAmount; this.freeTaxSalesAmount = freeTaxSalesAmount;
        this.zeroTaxSalesAmount = zeroTaxSalesAmount; this.taxType = taxType;
        this.taxRate = taxRate; this.taxAmount = taxAmount; this.totalAmount = totalAmount;
    }
}
