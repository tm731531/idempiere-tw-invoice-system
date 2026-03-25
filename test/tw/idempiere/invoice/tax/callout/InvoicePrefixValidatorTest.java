package tw.idempiere.invoice.tax.callout;

import org.junit.Test;
import static org.junit.Assert.*;

public class InvoicePrefixValidatorTest {

    @Test
    public void testValidPrefixCode_passes() {
        assertTrue(InvoicePrefixValidator.isValidPrefixCode("AA"));
        assertTrue(InvoicePrefixValidator.isValidPrefixCode("ZZ"));
    }

    @Test
    public void testInvalidPrefixCode_lowercase_fails() {
        assertFalse(InvoicePrefixValidator.isValidPrefixCode("aa"));
    }

    @Test
    public void testInvalidPrefixCode_tooShort_fails() {
        assertFalse(InvoicePrefixValidator.isValidPrefixCode("A"));
    }

    @Test
    public void testInvalidPrefixCode_null_fails() {
        assertFalse(InvoicePrefixValidator.isValidPrefixCode(null));
    }

    @Test
    public void testValidNumberRange_passes() {
        assertTrue(InvoicePrefixValidator.isValidNumberRange(1, 100));
    }

    @Test
    public void testInvalidNumberRange_startEqualsEnd_fails() {
        assertFalse(InvoicePrefixValidator.isValidNumberRange(100, 100));
    }

    @Test
    public void testInvalidNumberRange_startGreaterThanEnd_fails() {
        assertFalse(InvoicePrefixValidator.isValidNumberRange(101, 100));
    }

    @Test
    public void testStatusTransition_completeToActive_blocked() {
        assertTrue(InvoicePrefixValidator.isInvalidStatusTransition("C", "A"));
    }

    @Test
    public void testStatusTransition_inactiveToActive_allowed() {
        assertFalse(InvoicePrefixValidator.isInvalidStatusTransition("I", "A"));
    }

    @Test
    public void testStatusTransition_activeToComplete_allowed() {
        assertFalse(InvoicePrefixValidator.isInvalidStatusTransition("A", "C"));
    }
}
