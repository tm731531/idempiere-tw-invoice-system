package tw.idempiere.invoice.tax.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class MInvoiceAdjustmentConstantsTest {

    @Test
    public void testAdjustmentDirectionConstants() {
        assertEquals("SALES", MInvoiceAdjustment.ADJUSTMENTDIRECTION_Sales);
        assertEquals("PURCHASE", MInvoiceAdjustment.ADJUSTMENTDIRECTION_Purchase);
    }
}
