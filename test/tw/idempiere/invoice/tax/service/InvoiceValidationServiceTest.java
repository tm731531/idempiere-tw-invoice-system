package tw.idempiere.invoice.tax.service;

import java.time.LocalDate;
import org.junit.Test;
import static org.junit.Assert.*;

public class InvoiceValidationServiceTest {

    @Test
    public void testFutureDateRejected() {
        ValidationResult r = InvoiceValidationService.validateInvoiceDate(
            LocalDate.now().plusDays(1));
        assertFalse("Future date must be invalid", r.isValid());
        assertNotNull(r.getErrorMessage());
    }

    @Test
    public void testTodayDateAccepted() {
        ValidationResult r = InvoiceValidationService.validateInvoiceDate(LocalDate.now());
        assertTrue(r.isValid());
    }

    @Test
    public void testPastDateAccepted() {
        ValidationResult r = InvoiceValidationService.validateInvoiceDate(
            LocalDate.now().minusDays(1));
        assertTrue(r.isValid());
    }

    @Test
    public void testNonSequentialDate_warningNotBlock() {
        ValidationResult r = InvoiceValidationService.validateDateSequence(
            LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 1));
        assertTrue("Non-sequential date should not block save", r.isValid());
        assertTrue("Non-sequential date should produce warning", r.hasWarning());
    }

    @Test
    public void testSequentialDate_noWarning() {
        ValidationResult r = InvoiceValidationService.validateDateSequence(
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));
        assertTrue(r.isValid());
        assertFalse(r.hasWarning());
    }

    @Test
    public void testMonthMismatch_warningNotBlock() {
        ValidationResult r = InvoiceValidationService.validateMonthConsistency(
            LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 15));
        assertTrue(r.isValid());
        assertTrue(r.hasWarning());
    }

    @Test
    public void testMonthMatch_noWarning() {
        ValidationResult r = InvoiceValidationService.validateMonthConsistency(
            LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 20));
        assertTrue(r.isValid());
        assertFalse(r.hasWarning());
    }
}
