package tw.idempiere.invoice.tax.callout;

import org.junit.Test;
import static org.junit.Assert.*;

public class TaxStatementValidatorTest {

    @Test
    public void testValidateStatementPeriod_valid_1to6() {
        for (int p = 1; p <= 6; p++) {
            assertNull("Period " + p + " should be valid",
                TaxStatementValidator.validateStatementPeriod(p));
        }
    }

    @Test
    public void testValidateStatementPeriod_zero_skipsValidation() {
        // 0 means "not yet entered" on a new record — must not produce an error
        assertNull(TaxStatementValidator.validateStatementPeriod(0));
    }

    @Test
    public void testValidateStatementYear_zero_skipsValidation() {
        assertNull(TaxStatementValidator.validateStatementYear(0));
    }

    @Test
    public void testValidateStatementPeriod_seven_returnsError() {
        assertNotNull(TaxStatementValidator.validateStatementPeriod(7));
    }

    @Test
    public void testValidateStatementPeriod_negative_returnsError() {
        assertNotNull(TaxStatementValidator.validateStatementPeriod(-1));
    }

    @Test
    public void testValidateStatementYear_2026_isValid() {
        assertNull(TaxStatementValidator.validateStatementYear(2026));
    }

    @Test
    public void testValidateStatementYear_1999_returnsError() {
        assertNotNull(TaxStatementValidator.validateStatementYear(1999));
    }

    @Test
    public void testValidateStatementYear_2100_returnsError() {
        assertNotNull(TaxStatementValidator.validateStatementYear(2100));
    }
}
