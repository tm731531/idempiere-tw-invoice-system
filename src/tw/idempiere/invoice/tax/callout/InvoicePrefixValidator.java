package tw.idempiere.invoice.tax.callout;

import tw.idempiere.invoice.tax.model.MInvoicePrefix;

public class InvoicePrefixValidator {

    // --- Static helper methods (unit-testable without iDempiere runtime) ---

    public static boolean isValidPrefixCode(String code) {
        return code != null && code.matches("[A-Z]{2}");
    }

    public static boolean isValidNumberRange(int startNumber, int endNumber) {
        return startNumber < endNumber;
    }

    public static boolean isInvalidStatusTransition(String oldStatus, String newStatus) {
        // C cannot revert to A or I (completed state is irreversible)
        if ("C".equals(oldStatus) && "A".equals(newStatus)) return true;
        if ("C".equals(oldStatus) && "I".equals(newStatus)) return true;
        // I cannot skip A and jump directly to C (must go I→A→C)
        if ("I".equals(oldStatus) && "C".equals(newStatus)) return true;
        // A cannot revert to I (once active, prefix has likely been used to issue invoices)
        if ("A".equals(oldStatus) && "I".equals(newStatus)) return true;
        return false;
    }
}
