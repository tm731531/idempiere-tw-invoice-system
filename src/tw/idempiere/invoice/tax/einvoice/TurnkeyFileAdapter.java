package tw.idempiere.invoice.tax.einvoice;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.adempiere.exceptions.AdempiereException;

/** Drops one C0401 XML file (1 file = 1 invoice) into Turnkey's B2C intake directory. */
public final class TurnkeyFileAdapter implements TransmissionAdapter {

    private final String intakeDir;

    public TurnkeyFileAdapter(String intakeDir) { this.intakeDir = intakeDir; }

    @Override
    public String submit(String invoiceNumber, String xml) {
        File dir = new File(intakeDir);
        if (!dir.isDirectory())
            throw new AdempiereException("Turnkey intake dir not found: " + intakeDir);
        String name = invoiceNumber + ".xml";
        try {
            Files.write(new File(dir, name).toPath(), xml.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AdempiereException("Failed writing C0401 to Turnkey intake: " + e.getMessage(), e);
        }
        return name;
    }
}
