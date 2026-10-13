package tw.idempiere.invoice.tax.einvoice;

import java.math.BigDecimal;

public final class EInvoiceItem {
    public final String description;
    public final BigDecimal quantity, unitPrice, amount;
    public final String taxType, sequenceNumber, remark, relateNumber;

    public EInvoiceItem(String description, BigDecimal quantity, BigDecimal unitPrice, String taxType,
                         BigDecimal amount, String sequenceNumber, String remark, String relateNumber) {
        this.description = description; this.quantity = quantity;
        this.unitPrice = unitPrice; this.taxType = taxType; this.amount = amount;
        this.sequenceNumber = sequenceNumber; this.remark = remark; this.relateNumber = relateNumber;
    }
}
