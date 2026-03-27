package tw.idempiere.invoice.tax.callout;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.compiere.model.PO;
import org.osgi.service.event.Event;

import tw.idempiere.invoice.tax.model.MInvoicePrefix;

/**
 * Event handler for TW_InvoicePrefix table validation.
 *
 * Delegates to static helper methods in {@link InvoicePrefixValidator}
 * which are unit-testable without an iDempiere runtime.
 *
 * Registered via OSGI-INF/InvoicePrefixEventHandler.xml with an
 * IEventManager reference — the canonical iDempiere pattern for
 * model validation in OSGi plugins.
 */
public class InvoicePrefixEventHandler extends AbstractEventHandler {

    @Override
    protected void initialize() {
        registerTableEvent(IEventTopics.PO_BEFORE_NEW, MInvoicePrefix.Table_Name);
        registerTableEvent(IEventTopics.PO_BEFORE_CHANGE, MInvoicePrefix.Table_Name);
    }

    @Override
    protected void doHandleEvent(Event event) {
        PO po = getPO(event);
        if (!(po instanceof MInvoicePrefix))
            return;
        MInvoicePrefix prefix = (MInvoicePrefix) po;

        // Validate prefix code format
        String code = prefix.getPrefixCode();
        if (!InvoicePrefixValidator.isValidPrefixCode(code)) {
            addErrorMessage(event, "發票字軌代碼格式錯誤，需為2個大寫英文字母（如 AA, BZ）");
            return;
        }

        // Validate number range
        if (!InvoicePrefixValidator.isValidNumberRange(prefix.getStartNumber(), prefix.getEndNumber())) {
            addErrorMessage(event, "起始號碼必須小於結束號碼");
            return;
        }

        // Validate status transition (only on change, not new)
        String topic = event.getTopic();
        if (IEventTopics.PO_BEFORE_CHANGE.equals(topic)) {
            String oldStatus = (String) prefix.get_ValueOld(MInvoicePrefix.COLUMNNAME_Status);
            String newStatus = prefix.getStatus();
            if (InvoicePrefixValidator.isInvalidStatusTransition(oldStatus, newStatus)) {
                addErrorMessage(event, "字軌狀態不可逆轉，必須依 I→A→C 順序推進，已完成狀態不可回退");
            }
        }
    }
}
