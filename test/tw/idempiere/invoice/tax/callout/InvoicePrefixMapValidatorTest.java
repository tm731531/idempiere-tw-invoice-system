package tw.idempiere.invoice.tax.callout;

import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class InvoicePrefixMapValidatorTest {

    @Test
    public void testFutureInvoiceDate_blocked() {
        String result = InvoicePrefixMapValidator.validateInvoiceDateStatic(
            LocalDate.now().plusDays(1));
        assertNotNull("Future date should return error", result);
    }

    @Test
    public void testTodayInvoiceDate_passes() {
        String result = InvoicePrefixMapValidator.validateInvoiceDateStatic(LocalDate.now());
        assertNull("Today should pass", result);
    }

    @Test
    public void testTripartRequiresBuyerTaxID() {
        String result = InvoicePrefixMapValidator.validateBuyerTaxIDStatic("SALES_TRIPART", null);
        assertNotNull("TRIPART without TaxID should error", result);
    }

    @Test
    public void testBipartBuyerTaxIDOptional() {
        String result = InvoicePrefixMapValidator.validateBuyerTaxIDStatic("SALES_BIPART", null);
        assertNull("BIPART without TaxID is ok", result);
    }

    @Test
    public void testTripartWithBuyerTaxID_passes() {
        String result = InvoicePrefixMapValidator.validateBuyerTaxIDStatic("SALES_TRIPART", "12345678");
        assertNull("TRIPART with TaxID should pass", result);
    }

    @Test
    public void testTripartBuyerTaxID_sevenDigits_fails() {
        String result = InvoicePrefixMapValidator.validateBuyerTaxIDStatic("SALES_TRIPART", "1234567");
        assertNotNull("7-digit TaxID should fail", result);
    }

    @Test
    public void testTripartBuyerTaxID_nineDigits_fails() {
        String result = InvoicePrefixMapValidator.validateBuyerTaxIDStatic("SALES_TRIPART", "123456789");
        assertNotNull("9-digit TaxID should fail", result);
    }

    @Test
    public void testTripartBuyerTaxID_eightLetters_fails() {
        String result = InvoicePrefixMapValidator.validateBuyerTaxIDStatic("SALES_TRIPART", "ABCDEFGH");
        assertNotNull("8-letter TaxID should fail", result);
    }

    @Test
    public void testTripartBuyerTaxID_valid8Digits_passes() {
        String result = InvoicePrefixMapValidator.validateBuyerTaxIDStatic("SALES_TRIPART", "12345678");
        assertNull("Valid 8-digit TaxID should pass", result);
    }
}
