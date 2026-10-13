package tw.idempiere.invoice.tax.einvoice;

import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import org.adempiere.exceptions.AdempiereException;
import org.junit.Test;

public class TurnkeyFileAdapterTest {

    @Test
    public void writesXmlFileNamedByInvoiceNumber() throws Exception {
        File dir = Files.createTempDirectory("turnkey").toFile();
        TransmissionAdapter a = new TurnkeyFileAdapter(dir.getAbsolutePath());
        String name = a.submit("AA00000001", "<Invoice/>");
        File out = new File(dir, name);
        assertTrue(out.exists());
        assertEquals("AA00000001.xml", name);
        assertEquals("<Invoice/>", new String(Files.readAllBytes(out.toPath()), "UTF-8"));
    }

    @Test(expected = AdempiereException.class)
    public void throwsWhenDirMissing() {
        new TurnkeyFileAdapter("/nonexistent/turnkey/intake").submit("AA00000001", "<Invoice/>");
    }
}
