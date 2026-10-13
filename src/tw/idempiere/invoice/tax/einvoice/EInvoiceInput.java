package tw.idempiere.invoice.tax.einvoice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class EInvoiceInput {
    public final String sellerTaxId, sellerName, buyerTaxId, invoiceNumber;
    public final LocalDateTime invoiceDate;
    public final BigDecimal salesAmount, taxAmount, totalAmount;
    public final List<EInvoiceItem> items;
    public EInvoiceInput(String sellerTaxId, String sellerName, String buyerTaxId,
                         String invoiceNumber, LocalDateTime invoiceDate,
                         BigDecimal salesAmount, BigDecimal taxAmount, BigDecimal totalAmount,
                         List<EInvoiceItem> items) {
        this.sellerTaxId = sellerTaxId; this.sellerName = sellerName;
        this.buyerTaxId = buyerTaxId; this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate; this.salesAmount = salesAmount;
        this.taxAmount = taxAmount; this.totalAmount = totalAmount; this.items = items;
    }
}
