package tw.idempiere.invoice.tax.callout;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.compiere.model.PO;
import org.osgi.service.event.Event;

import tw.idempiere.invoice.tax.model.MTaxStatement;

/**
 * Event handler for TW_TaxStatement table validation.
 *
 * Delegates to static helper methods in {@link TaxStatementValidator}
 * which are unit-testable without an iDempiere runtime.
 */
public class TaxStatementEventHandler extends AbstractEventHandler {

    @Override
    protected void initialize() {
        registerTableEvent(IEventTopics.PO_BEFORE_NEW, MTaxStatement.Table_Name);
        registerTableEvent(IEventTopics.PO_BEFORE_CHANGE, MTaxStatement.Table_Name);
    }

    @Override
    protected void doHandleEvent(Event event) {
        PO po = getPO(event);
        if (!(po instanceof MTaxStatement))
            return;
        MTaxStatement stmt = (MTaxStatement) po;

        // Validate StatementPeriod is 1–6
        String periodError = TaxStatementValidator.validateStatementPeriod(stmt.getStatementPeriod());
        if (periodError != null) {
            addErrorMessage(event, periodError);
            return;
        }

        // Validate StatementYear is reasonable
        String yearError = TaxStatementValidator.validateStatementYear(stmt.getStatementYear());
        if (yearError != null) {
            addErrorMessage(event, yearError);
            return;
        }

        // TODO: Implement MixedBusiness < 9 months adjustment exemption
        // Per domain rule: if the company has been operating for < 9 months, the mixed-business
        // ratio adjustment is not required. MixedBusinessService.isEligibleForAdjustment()
        // exists but no event handler calls it to enforce or warn. Add a check here when
        // isMixedBusiness() is true.
    }
}
