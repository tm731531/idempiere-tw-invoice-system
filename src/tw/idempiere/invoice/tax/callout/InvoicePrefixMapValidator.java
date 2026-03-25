package tw.idempiere.invoice.tax.callout;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.logging.Logger;
import org.compiere.model.MClient;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import tw.idempiere.invoice.tax.model.MInvoicePrefixMap;

public class InvoicePrefixMapValidator implements ModelValidator {

    private static final Logger log = Logger.getLogger(InvoicePrefixMapValidator.class.getName());

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

    @Override
    public void initialize(ModelValidationEngine engine, MClient client) {
        engine.addModelChange(MInvoicePrefixMap.Table_Name, this);
        log.info("InvoicePrefixMapValidator registered for " + MInvoicePrefixMap.Table_Name);
    }

    @Override
    public String modelChange(PO po, int type) throws Exception {
        if (!(po instanceof MInvoicePrefixMap)) return null;
        MInvoicePrefixMap map = (MInvoicePrefixMap) po;

        if (type == ModelValidator.TYPE_BEFORE_NEW || type == ModelValidator.TYPE_BEFORE_CHANGE) {
            Timestamp ts = map.getDateInvoiced();
            if (ts != null) {
                String dateError = validateInvoiceDateStatic(ts.toLocalDateTime().toLocalDate());
                if (dateError != null) return dateError;
            }
            String taxIdError = validateBuyerTaxIDStatic(map.getInvoiceType(), map.getBuyerTaxID());
            if (taxIdError != null) return taxIdError;
        }
        return null;
    }

    @Override
    public String docValidate(PO po, int timing) { return null; }

    @Override
    public int getAD_Client_ID() { return 0; }

    @Override
    public String login(int AD_Org_ID, int AD_Role_ID, int AD_User_ID) { return null; }
}
