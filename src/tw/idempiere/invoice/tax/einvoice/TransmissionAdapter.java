package tw.idempiere.invoice.tax.einvoice;

/** Hands a MIG XML message off to a transmission backend. */
public interface TransmissionAdapter {
    /** @return the written/submitted file name; throws AdempiereException on failure. */
    String submit(String invoiceNumber, String xml);
}
