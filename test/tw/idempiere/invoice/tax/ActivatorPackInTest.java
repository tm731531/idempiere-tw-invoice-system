package tw.idempiere.invoice.tax;

import org.junit.Test;
import static org.junit.Assert.*;

public class ActivatorPackInTest {
    @Test
    public void testPackInResourceExists() {
        java.net.URL zipUrl = TaiwanInvoiceTaxActivator.class
            .getResource("/META-INF/2Pack_1.0.4.zip");
        assertNotNull("2Pack ZIP must exist at /META-INF/2Pack_1.0.4.zip", zipUrl);
    }
}
