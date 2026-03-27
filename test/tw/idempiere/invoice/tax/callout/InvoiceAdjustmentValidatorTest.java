package tw.idempiere.invoice.tax.callout;

import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class InvoiceAdjustmentValidatorTest {

    @Test
    public void testValidateAdjustmentDate_futureDate_returnsError() {
        String result = InvoiceAdjustmentValidator.validateAdjustmentDate(LocalDate.now().plusDays(1));
        assertNotNull(result);
    }

    @Test
    public void testValidateAdjustmentDate_today_isValid() {
        assertNull(InvoiceAdjustmentValidator.validateAdjustmentDate(LocalDate.now()));
    }

    @Test
    public void testValidateAdjustmentDate_pastDate_isValid() {
        assertNull(InvoiceAdjustmentValidator.validateAdjustmentDate(LocalDate.now().minusDays(1)));
    }

    @Test
    public void testValidateAdjustmentDate_null_isValid() {
        assertNull(InvoiceAdjustmentValidator.validateAdjustmentDate(null));
    }

    @Test
    public void testValidateAdjustmentDirection_null_returnsError() {
        assertNotNull(InvoiceAdjustmentValidator.validateAdjustmentDirection(null));
    }

    @Test
    public void testValidateAdjustmentDirection_empty_returnsError() {
        assertNotNull(InvoiceAdjustmentValidator.validateAdjustmentDirection(""));
    }

    @Test
    public void testValidateAdjustmentDirection_SALES_isValid() {
        assertNull(InvoiceAdjustmentValidator.validateAdjustmentDirection("SALES"));
    }

    @Test
    public void testValidateAdjustmentDirection_PURCHASE_isValid() {
        assertNull(InvoiceAdjustmentValidator.validateAdjustmentDirection("PURCHASE"));
    }
}
