package tw.idempiere.invoice.tax.process;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import org.junit.Test;
import tw.idempiere.invoice.tax.einvoice.AmountInfo;

public class GenerateEInvoiceAmountsTest {
    @Test public void b2cDoesNotSplitTax() {
        AmountInfo a = GenerateEInvoiceProcess.b2cAmount(new BigDecimal("325"));
        assertEquals(new BigDecimal("325"), a.salesAmount);
        assertEquals(new BigDecimal("325"), a.totalAmount);
        assertEquals(BigDecimal.ZERO, a.taxAmount);
        assertEquals("1", a.taxType);
    }
}
