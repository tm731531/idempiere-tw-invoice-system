package tw.idempiere.invoice.tax.einvoice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.adempiere.exceptions.AdempiereException;

/** Canonical C0401 document — defaults applied + validated. Pure Java. */
public final class EInvoiceDoc {
    private final EInvoiceInput in;
    private final String randomNumber, buyerTaxId;
    private final String donateMark = "0", taxType = "1", invoiceType = "07";
    private final BigDecimal taxRate = new BigDecimal("0.05");

    private EInvoiceDoc(EInvoiceInput in, String randomNumber, String buyerTaxId) {
        this.in = in; this.randomNumber = randomNumber; this.buyerTaxId = buyerTaxId;
    }

    public static EInvoiceDoc forB2CTaxable(EInvoiceInput in, String randomNumber) {
        if (in.sellerTaxId == null || in.sellerTaxId.trim().isEmpty())
            throw new AdempiereException("E-Invoice: seller tax id (賣方統編) is required");
        if (in.items == null || in.items.isEmpty())
            throw new AdempiereException("E-Invoice: at least one line item (品項) is required");
        if (in.invoiceNumber == null || !in.invoiceNumber.matches("[A-Z]{2}\\d{8}"))
            throw new AdempiereException("E-Invoice: invoice number must be AA00000001 format");
        String buyer = (in.buyerTaxId == null || in.buyerTaxId.trim().isEmpty())
                ? "0000000000" : in.buyerTaxId.trim();
        return new EInvoiceDoc(in, randomNumber, buyer);
    }

    public String getSellerTaxId()  { return in.sellerTaxId; }
    public String getSellerName()   { return in.sellerName; }
    public String getBuyerTaxId()   { return buyerTaxId; }
    public String getInvoiceNumber(){ return in.invoiceNumber; }
    public LocalDateTime getInvoiceDate() { return in.invoiceDate; }
    public BigDecimal getSalesAmount()    { return in.salesAmount; }
    public BigDecimal getTaxAmount()      { return in.taxAmount; }
    public BigDecimal getTotalAmount()    { return in.totalAmount; }
    public List<EInvoiceItem> getItems()  { return in.items; }
    public String getRandomNumber() { return randomNumber; }
    public String getDonateMark()   { return donateMark; }
    public String getTaxType()      { return taxType; }
    public String getInvoiceType()  { return invoiceType; }
    public BigDecimal getTaxRate()  { return taxRate; }
}
