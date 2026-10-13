package tw.idempiere.invoice.tax.einvoice;

/** F0401 Main/Buyer block. name/address are nullable (element omitted when null/blank). */
public final class BuyerInfo {
    public final String identifier, name, address;

    public BuyerInfo(String identifier, String name, String address) {
        this.identifier = identifier; this.name = name; this.address = address;
    }
}
