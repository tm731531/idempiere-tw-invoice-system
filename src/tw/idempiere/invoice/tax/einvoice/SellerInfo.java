package tw.idempiere.invoice.tax.einvoice;

/** F0401 Main/Seller block. Everything after address is nullable (element omitted when null/blank). */
public final class SellerInfo {
    public final String identifier, name, address;
    public final String personInCharge, telephoneNumber, facsimileNumber, emailAddress, customerNumber, roleRemark;

    public SellerInfo(String identifier, String name, String address, String personInCharge,
                       String telephoneNumber, String facsimileNumber, String emailAddress,
                       String customerNumber, String roleRemark) {
        this.identifier = identifier; this.name = name; this.address = address;
        this.personInCharge = personInCharge; this.telephoneNumber = telephoneNumber;
        this.facsimileNumber = facsimileNumber; this.emailAddress = emailAddress;
        this.customerNumber = customerNumber; this.roleRemark = roleRemark;
    }
}
