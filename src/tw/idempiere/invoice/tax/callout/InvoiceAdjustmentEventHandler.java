package tw.idempiere.invoice.tax.callout;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.compiere.model.PO;
import org.osgi.service.event.Event;

import tw.idempiere.invoice.tax.model.MInvoiceAdjustment;

import java.sql.Timestamp;
import java.time.ZoneId;

/**
 * Event handler for TW_InvoiceAdjustment table validation.
 *
 * Delegates to static helper methods in {@link InvoiceAdjustmentValidator}
 * which are unit-testable without an iDempiere runtime.
 */
public class InvoiceAdjustmentEventHandler extends AbstractEventHandler {

    @Override
    protected void initialize() {
        registerTableEvent(IEventTopics.PO_BEFORE_NEW, MInvoiceAdjustment.Table_Name);
        registerTableEvent(IEventTopics.PO_BEFORE_CHANGE, MInvoiceAdjustment.Table_Name);
    }

    @Override
    protected void doHandleEvent(Event event) {
        PO po = getPO(event);
        if (!(po instanceof MInvoiceAdjustment))
            return;
        MInvoiceAdjustment adj = (MInvoiceAdjustment) po;

        // Validate AdjustmentDirection is set
        String dirError = InvoiceAdjustmentValidator.validateAdjustmentDirection(adj.getAdjustmentDirection());
        if (dirError != null) {
            addErrorMessage(event, dirError);
            return;
        }

        // Validate AdjustmentDate is not in the future
        Timestamp ts = adj.getAdjustmentDate();
        if (ts != null) {
            String dateError = InvoiceAdjustmentValidator.validateAdjustmentDate(ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
            if (dateError != null) {
                addErrorMessage(event, dateError);
                return;
            }
        }

        // TODO: Implement overdue period warning (超期進項折讓)
        // Per domain rule: late purchase adjustments carry tax penalty risk and require
        // user confirmation beyond a simple warning. IsOverduePeriod flag exists in the model
        // but no enforcement is currently performed. Implement as a mandatory confirmation
        // dialog when IsOverduePeriod = true, not just a warning message.
        //
        // TODO: Implement 10-year input tax expiry check
        // Per domain rule: input tax credits expire after 10 years. Warn 90 days before expiry.
        // Requires computing expiry date from AdjustmentDate and comparing against today.
    }
}
