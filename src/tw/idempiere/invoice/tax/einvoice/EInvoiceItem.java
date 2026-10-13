package tw.idempiere.invoice.tax.einvoice;

import java.math.BigDecimal;

public final class EInvoiceItem {
    public final String description;
    public final BigDecimal quantity, unitPrice, amount;
    public EInvoiceItem(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
        this.description = description; this.quantity = quantity;
        this.unitPrice = unitPrice; this.amount = amount;
    }
}
