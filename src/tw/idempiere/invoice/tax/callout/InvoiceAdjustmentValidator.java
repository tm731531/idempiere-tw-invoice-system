package tw.idempiere.invoice.tax.callout;

import java.time.LocalDate;

/**
 * Static validation helpers for TW_InvoiceAdjustment.
 * Pure Java — no iDempiere runtime dependency. Fully unit-testable.
 */
public class InvoiceAdjustmentValidator {

    private InvoiceAdjustmentValidator() {}

    /**
     * AdjustmentDate must not be a future date.
     * @return error message, or null if valid
     */
    public static String validateAdjustmentDate(LocalDate adjustmentDate) {
        if (adjustmentDate != null && adjustmentDate.isAfter(LocalDate.now()))
            return "折讓日期不可為未來日期";
        return null;
    }

    /**
     * AdjustmentDirection must be set (cannot be null or empty).
     * @return error message, or null if valid
     */
    public static String validateAdjustmentDirection(String adjustmentDirection) {
        if (adjustmentDirection == null || adjustmentDirection.trim().isEmpty())
            return "折讓方向為必填（SALES=銷項折讓 / PURCHASE=進項折讓）";
        if (!"SALES".equals(adjustmentDirection) && !"PURCHASE".equals(adjustmentDirection))
            return "折讓方向值無效，僅接受 SALES（銷項折讓）或 PURCHASE（進項折讓）";
        return null;
    }
}
