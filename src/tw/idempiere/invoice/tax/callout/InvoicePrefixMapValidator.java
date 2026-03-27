package tw.idempiere.invoice.tax.callout;

import java.time.LocalDate;

public class InvoicePrefixMapValidator {

    public static String validateInvoiceDateStatic(LocalDate invoiceDate) {
        if (invoiceDate != null && invoiceDate.isAfter(LocalDate.now()))
            return "發票日期不可為未來日期";
        return null;
    }

    public static String validateBuyerTaxIDStatic(String invoiceType, String buyerTaxID) {
        if ("SALES_TRIPART".equals(invoiceType)) {
            if (buyerTaxID == null || buyerTaxID.trim().isEmpty())
                return "三聯式發票必須填寫買方統一編號";
            if (!buyerTaxID.matches("\\d{8}"))
                return "買方統一編號必須為8位數字";
        }
        return null;
    }
}
