package tw.idempiere.invoice.tax.einvoice;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.adempiere.exceptions.AdempiereException;
import org.junit.Test;

public class EInvoiceDocTest {

    private EInvoiceInput input(String buyerTaxId, List<EInvoiceItem> items) {
        return new EInvoiceInput("12345678", "Test Seller Co", buyerTaxId,
                "AA00000001", LocalDateTime.of(2026, 10, 13, 9, 30, 0),
                new BigDecimal("100"), new BigDecimal("5"), new BigDecimal("105"), items);
    }

    private List<EInvoiceItem> oneItem() {
        return Collections.singletonList(
            new EInvoiceItem("Widget", new BigDecimal("1"), new BigDecimal("105"), new BigDecimal("105")));
    }

    @Test
    public void b2cBlankBuyerDefaultsToAllZeros() {
        EInvoiceDoc doc = EInvoiceDoc.forB2CTaxable(input(null, oneItem()), "0007");
        assertEquals("0000000000", doc.getBuyerTaxId());
        assertEquals("0", doc.getDonateMark());
        assertEquals("1", doc.getTaxType());
        assertEquals("0007", doc.getRandomNumber());
        assertEquals("07", doc.getInvoiceType());
    }

    @Test(expected = AdempiereException.class)
    public void rejectsMissingSellerTaxId() {
        EInvoiceInput bad = new EInvoiceInput("", "S", null, "AA00000001",
                LocalDateTime.now(), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, oneItem());
        EInvoiceDoc.forB2CTaxable(bad, "0007");
    }

    @Test(expected = AdempiereException.class)
    public void rejectsNoLines() {
        EInvoiceDoc.forB2CTaxable(input(null, Collections.emptyList()), "0007");
    }
}
