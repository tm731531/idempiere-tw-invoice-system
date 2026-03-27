package tw.idempiere.invoice.tax;

import org.junit.Test;
import static org.junit.Assert.*;

public class ActivatorPackInTest {
    // NOTE: This version string must be updated whenever the 2Pack version is bumped
    // (e.g., 1.0.9 → 1.0.10). If this test fails with a null URL after a version bump,
    // update the filename below and in resources/META-INF/ to match.
    private static final String PACK_VERSION = "1.0.10";

    @Test
    public void testPackInResourceExists() {
        String zipPath = "/META-INF/2Pack_" + PACK_VERSION + ".zip";
        java.net.URL zipUrl = TaiwanInvoiceTaxActivator.class.getResource(zipPath);
        assertNotNull("2Pack ZIP must exist at " + zipPath
            + " — update PACK_VERSION constant if version was bumped", zipUrl);
    }
}
