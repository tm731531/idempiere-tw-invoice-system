package tw.idempiere.invoice.tax.callout;

/**
 * Static validation helpers for TW_TaxStatement.
 * Pure Java — no iDempiere runtime dependency. Fully unit-testable.
 */
public class TaxStatementValidator {

    private TaxStatementValidator() {}

    /**
     * StatementPeriod must be 1–6 (bimonthly periods: Jan-Feb=1 ... Nov-Dec=6).
     * @return error message, or null if valid
     */
    public static String validateStatementPeriod(int statementPeriod) {
        if (statementPeriod == 0) return null;  // not yet entered — skip validation on new record
        if (statementPeriod < 1 || statementPeriod > 6)
            return "申報期別必須為 1-6（1=1-2月，2=3-4月，...，6=11-12月）";
        return null;
    }

    /**
     * StatementYear must be a 4-digit year (reasonable range: 2000–2099).
     * @return error message, or null if valid
     */
    public static String validateStatementYear(int statementYear) {
        if (statementYear == 0) return null;  // not yet entered — skip validation on new record
        if (statementYear < 2000 || statementYear > 2099)
            return "申報年份必須為西元年（2000-2099）";
        return null;
    }
}
