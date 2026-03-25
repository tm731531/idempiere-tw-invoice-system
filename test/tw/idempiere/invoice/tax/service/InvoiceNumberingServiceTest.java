package tw.idempiere.invoice.tax.service;

import org.junit.Test;
import static org.junit.Assert.*;

public class InvoiceNumberingServiceTest {
    @Test
    public void testFormatInvoiceNumber_tripart() {
        String result = InvoiceNumberingService.formatInvoiceNumber("AA", 1);
        assertEquals("AA00000001", result);
    }

    @Test
    public void testFormatInvoiceNumber_paddingEight() {
        String result = InvoiceNumberingService.formatInvoiceNumber("ZZ", 99999999);
        assertEquals("ZZ99999999", result);
    }

    @Test
    public void testIsExhausted_whenCurrentExceedsEnd() {
        assertTrue(InvoiceNumberingService.isExhausted(100, 99));
    }

    @Test
    public void testIsExhausted_whenCurrentEqualsEnd() {
        assertFalse(InvoiceNumberingService.isExhausted(99, 99));
    }

    @Test
    public void testIsExhausted_whenCurrentBelowEnd() {
        assertFalse(InvoiceNumberingService.isExhausted(1, 99));
    }
}
