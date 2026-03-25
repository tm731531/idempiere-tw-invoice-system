package tw.idempiere.invoice.tax.callout;

import java.util.logging.Logger;
import org.compiere.model.MClient;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import tw.idempiere.invoice.tax.model.MInvoicePrefix;

public class InvoicePrefixValidator implements ModelValidator {

    private static final Logger log = Logger.getLogger(InvoicePrefixValidator.class.getName());

    // --- Static helper methods (unit-testable without iDempiere runtime) ---

    public static boolean isValidPrefixCode(String code) {
        return code != null && code.matches("[A-Z]{2}");
    }

    public static boolean isValidNumberRange(int startNumber, int endNumber) {
        return startNumber < endNumber;
    }

    public static boolean isInvalidStatusTransition(String oldStatus, String newStatus) {
        return "C".equals(oldStatus) && "A".equals(newStatus);
    }

    // --- iDempiere ModelValidator implementation ---

    @Override
    public void initialize(ModelValidationEngine engine, MClient client) {
        engine.addModelChange(MInvoicePrefix.Table_Name, this);
        log.info("InvoicePrefixValidator registered for " + MInvoicePrefix.Table_Name);
    }

    @Override
    public String modelChange(PO po, int type) throws Exception {
        if (!(po instanceof MInvoicePrefix)) return null;
        MInvoicePrefix prefix = (MInvoicePrefix) po;

        if (type == ModelValidator.TYPE_BEFORE_NEW || type == ModelValidator.TYPE_BEFORE_CHANGE) {
            // Rule 1: PrefixCode must be 2 uppercase letters
            if (!isValidPrefixCode(prefix.getPrefixCode()))
                return "字軌代號必須為2個大寫英文字母";

            // Rule 2: StartNumber < EndNumber
            if (!isValidNumberRange(prefix.getStartNumber(), prefix.getEndNumber()))
                return "起始號碼必須小於結束號碼";

            // Rule 3: Status C cannot revert to A
            if (type == ModelValidator.TYPE_BEFORE_CHANGE) {
                String oldStatus = (String) prefix.get_ValueOld(MInvoicePrefix.COLUMNNAME_PrefixStatus);
                String newStatus = prefix.getPrefixStatus();
                if (isInvalidStatusTransition(oldStatus, newStatus))
                    return "已完成的字軌不可重新設為使用中";
            }
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
