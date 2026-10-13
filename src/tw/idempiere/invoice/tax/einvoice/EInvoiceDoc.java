package tw.idempiere.invoice.tax.einvoice;

import java.time.LocalDateTime;
import java.util.List;
import org.adempiere.exceptions.AdempiereException;

/** Canonical F0401 document — defaults applied + validated. Pure Java. */
public final class EInvoiceDoc {
    private final String invoiceNumber;
    private final LocalDateTime invoiceDateTime;
    private final SellerInfo seller;
    private final BuyerInfo buyer;
    private final String invoiceType;
    private final List<EInvoiceItem> items;
    private final AmountInfo amount;
    private final String randomNumber;
    private final String donateMark = "0";
    private final String printMark = "Y";

    private EInvoiceDoc(String invoiceNumber, LocalDateTime invoiceDateTime, SellerInfo seller, BuyerInfo buyer,
                         String invoiceType, List<EInvoiceItem> items, AmountInfo amount, String randomNumber) {
        this.invoiceNumber = invoiceNumber; this.invoiceDateTime = invoiceDateTime;
        this.seller = seller; this.buyer = buyer; this.invoiceType = invoiceType;
        this.items = items; this.amount = amount; this.randomNumber = randomNumber;
    }

    public static EInvoiceDoc forB2CPrint(String invoiceNumber, LocalDateTime invoiceDateTime,
            SellerInfo seller, BuyerInfo buyer, String invoiceType, List<EInvoiceItem> items,
            AmountInfo amount, String randomNumber) {
        if (seller == null || seller.identifier == null || seller.identifier.trim().isEmpty())
            throw new AdempiereException("E-Invoice: seller identifier (賣方統編) is required");
        if (items == null || items.isEmpty())
            throw new AdempiereException("E-Invoice: at least one line item (品項) is required");
        if (invoiceNumber == null || !invoiceNumber.matches("[A-Z]{2}\\d{8}"))
            throw new AdempiereException("E-Invoice: invoice number must be AA00000001 format");
        return new EInvoiceDoc(invoiceNumber, invoiceDateTime, seller, buyer, invoiceType, items, amount, randomNumber);
    }

    public String getInvoiceNumber()      { return invoiceNumber; }
    public LocalDateTime getInvoiceDateTime() { return invoiceDateTime; }
    public SellerInfo getSeller()         { return seller; }
    public BuyerInfo getBuyer()           { return buyer; }
    public String getInvoiceType()        { return invoiceType; }
    public List<EInvoiceItem> getItems()  { return items; }
    public AmountInfo getAmount()         { return amount; }
    public String getRandomNumber()       { return randomNumber; }
    public String getDonateMark()         { return donateMark; }
    public String getPrintMark()          { return printMark; }
}
