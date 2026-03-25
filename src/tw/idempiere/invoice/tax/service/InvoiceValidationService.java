package tw.idempiere.invoice.tax.service;

import java.time.LocalDate;

public class InvoiceValidationService {

    public static ValidationResult validateInvoiceDate(LocalDate invoiceDate) {
        if (invoiceDate.isAfter(LocalDate.now()))
            return ValidationResult.error("發票日期不可為未來日期");
        return ValidationResult.ok();
    }

    public static ValidationResult validateDateSequence(LocalDate lastDate, LocalDate newDate) {
        if (newDate.isBefore(lastDate))
            return ValidationResult.ok().withWarning("發票日期早於同字軌最後開立日期，請確認");
        return ValidationResult.ok();
    }

    public static ValidationResult validateMonthConsistency(LocalDate invoiceDate, LocalDate trxDate) {
        if (invoiceDate.getMonth() != trxDate.getMonth() ||
            invoiceDate.getYear() != trxDate.getYear())
            return ValidationResult.ok().withWarning("發票月份與交易月份不一致，請確認");
        return ValidationResult.ok();
    }
}
