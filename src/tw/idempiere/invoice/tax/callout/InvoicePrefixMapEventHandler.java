package tw.idempiere.invoice.tax.callout;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.compiere.model.PO;
import org.osgi.service.event.Event;

import tw.idempiere.invoice.tax.model.MInvoicePrefixMap;

/**
 * Event handler for TW_Invoice_Prefix_Map table validation.
 *
 * Delegates to static helper methods in {@link InvoicePrefixMapValidator}
 * which are unit-testable without an iDempiere runtime.
 *
 * Registered via OSGI-INF/InvoicePrefixMapEventHandler.xml with an
 * IEventManager reference — the canonical iDempiere pattern for
 * model validation in OSGi plugins.
 */
public class InvoicePrefixMapEventHandler extends AbstractEventHandler {

    @Override
    protected void initialize() {
        registerTableEvent(IEventTopics.PO_BEFORE_NEW, MInvoicePrefixMap.Table_Name);
        registerTableEvent(IEventTopics.PO_BEFORE_CHANGE, MInvoicePrefixMap.Table_Name);
    }

    @Override
    protected void doHandleEvent(Event event) {
        PO po = getPO(event);
        if (!(po instanceof MInvoicePrefixMap))
            return;
        MInvoicePrefixMap map = (MInvoicePrefixMap) po;

        // Validate invoice date (no future dates)
        Timestamp ts = map.getDateInvoiced();
        if (ts != null) {
            LocalDate invoiceDate = ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            String dateError = InvoicePrefixMapValidator.validateInvoiceDateStatic(invoiceDate);
            if (dateError != null) {
                addErrorMessage(event, dateError);
                return;
            }
        }

        // Validate buyer tax ID for B2B invoices
        String buyerTaxIdError = InvoicePrefixMapValidator.validateBuyerTaxIDStatic(
            map.getInvoiceType(), map.getBuyerTaxID());
        if (buyerTaxIdError != null) {
            addErrorMessage(event, buyerTaxIdError);
        }
    }
}
