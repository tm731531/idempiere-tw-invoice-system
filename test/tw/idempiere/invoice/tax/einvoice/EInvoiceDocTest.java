package tw.idempiere.invoice.tax.einvoice;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.adempiere.exceptions.AdempiereException;
import org.junit.Test;

public class EInvoiceDocTest {

    private SellerInfo seller(String identifier) {
        return new SellerInfo(identifier, "Test Seller Co", "Some Address",
                null, null, null, null, null, null);
    }

    private BuyerInfo buyer() {
        return new BuyerInfo("0000000000", null, null);
    }

    private List<EInvoiceItem> oneItem() {
        return Collections.singletonList(
            new EInvoiceItem("Widget", new BigDecimal("1"), new BigDecimal("105"), "1",
                new BigDecimal("105"), "001", null, null));
    }

    private AmountInfo amount() {
        return new AmountInfo(new BigDecimal("100"), new BigDecimal("0"), new BigDecimal("0"),
                "1", new BigDecimal("0.05"), new BigDecimal("5"), new BigDecimal("105"));
    }

    @Test(expected = AdempiereException.class)
    public void rejectsMissingSellerTaxId() {
        EInvoiceDoc.forB2CPrint("AA00000001", LocalDateTime.now(), seller(""), buyer(),
                "07", oneItem(), amount(), "0007");
    }

    @Test(expected = AdempiereException.class)
    public void rejectsNoLines() {
        EInvoiceDoc.forB2CPrint("AA00000001", LocalDateTime.now(), seller("12345678"), buyer(),
                "07", Collections.emptyList(), amount(), "0007");
    }
}
