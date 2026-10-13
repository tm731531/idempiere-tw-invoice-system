package tw.idempiere.invoice.tax.einvoice;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/** Builds the MIG 4.1 F0401 (B2C 存證開立, 列印紙本) XML from an {@link EInvoiceDoc}. */
public final class MigF0401Builder {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private MigF0401Builder() { }

    public static String build(EInvoiceDoc doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<Invoice xsi:schemaLocation=\"urn:GEINV:eInvoiceMessage:F0401:4.1 F0401.xsd\" "
                + "xmlns=\"urn:GEINV:eInvoiceMessage:F0401:4.1\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");

        sb.append("    <Main>\n");
        sb.append(el("        ", "InvoiceNumber", doc.getInvoiceNumber()));
        sb.append(el("        ", "InvoiceDate", doc.getInvoiceDateTime().format(DATE_FMT)));
        sb.append(el("        ", "InvoiceTime", doc.getInvoiceDateTime().format(TIME_FMT)));

        SellerInfo seller = doc.getSeller();
        sb.append("        <Seller>\n");
        sb.append(el("            ", "Identifier", seller.identifier));
        sb.append(el("            ", "Name", seller.name));
        sb.append(el("            ", "Address", seller.address));
        sb.append(elOpt("            ", "PersonInCharge", seller.personInCharge));
        sb.append(elOpt("            ", "TelephoneNumber", seller.telephoneNumber));
        sb.append(elOpt("            ", "FacsimileNumber", seller.facsimileNumber));
        sb.append(elOpt("            ", "EmailAddress", seller.emailAddress));
        sb.append(elOpt("            ", "CustomerNumber", seller.customerNumber));
        sb.append(elOpt("            ", "RoleRemark", seller.roleRemark));
        sb.append("        </Seller>\n");

        BuyerInfo buyer = doc.getBuyer();
        sb.append("        <Buyer>\n");
        sb.append(el("            ", "Identifier", buyer.identifier));
        sb.append(elOpt("            ", "Name", buyer.name));
        sb.append(elOpt("            ", "Address", buyer.address));
        sb.append("        </Buyer>\n");

        sb.append(el("        ", "InvoiceType", doc.getInvoiceType()));
        sb.append(el("        ", "DonateMark", doc.getDonateMark()));
        sb.append(el("        ", "PrintMark", doc.getPrintMark()));
        sb.append(el("        ", "RandomNumber", doc.getRandomNumber()));
        sb.append("    </Main>\n");

        sb.append("    <Details>\n");
        for (EInvoiceItem item : doc.getItems()) {
            sb.append("        <ProductItem>\n");
            sb.append(el("            ", "Description", item.description));
            sb.append(el("            ", "Quantity", plain(item.quantity)));
            sb.append(el("            ", "UnitPrice", plain(item.unitPrice)));
            sb.append(el("            ", "TaxType", item.taxType));
            sb.append(el("            ", "Amount", plain(item.amount)));
            sb.append(el("            ", "SequenceNumber", item.sequenceNumber));
            sb.append(elOpt("            ", "Remark", item.remark));
            sb.append(elOpt("            ", "RelateNumber", item.relateNumber));
            sb.append("        </ProductItem>\n");
        }
        sb.append("    </Details>\n");

        AmountInfo amount = doc.getAmount();
        sb.append("    <Amount>\n");
        sb.append(el("        ", "SalesAmount", plain(amount.salesAmount)));
        sb.append(el("        ", "FreeTaxSalesAmount", plain(amount.freeTaxSalesAmount)));
        sb.append(el("        ", "ZeroTaxSalesAmount", plain(amount.zeroTaxSalesAmount)));
        sb.append(el("        ", "TaxType", amount.taxType));
        sb.append(el("        ", "TaxRate", plain(amount.taxRate)));
        sb.append(el("        ", "TaxAmount", plain(amount.taxAmount)));
        sb.append(el("        ", "TotalAmount", plain(amount.totalAmount)));
        sb.append("    </Amount>\n");

        sb.append("</Invoice>");
        return sb.toString();
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String el(String indent, String tag, String value) {
        return indent + "<" + tag + ">" + escape(value) + "</" + tag + ">\n";
    }

    private static String elOpt(String indent, String tag, String value) {
        if (value == null || value.trim().isEmpty()) return "";
        return el(indent, tag, value);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
