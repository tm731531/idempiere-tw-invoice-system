package tw.idempiere.invoice.tax;

import org.junit.Test;
import static org.junit.Assert.*;

public class ActivatorPackInTest {
    @Test
    public void testPackInResourceExists() {
        java.net.URL zipUrl = TaiwanInvoiceTaxActivator.class
            .getResource("/2pack/tw_invoice_system.zip");
        assertNotNull("2Pack ZIP must exist at /2pack/tw_invoice_system.zip", zipUrl);
    }
}
