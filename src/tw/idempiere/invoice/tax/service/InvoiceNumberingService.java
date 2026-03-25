package tw.idempiere.invoice.tax.service;

public class InvoiceNumberingService {
    public static String formatInvoiceNumber(String prefixCode, int number) {
        return prefixCode + String.format("%08d", number);
    }

    public static boolean isExhausted(int currentNumber, int endNumber) {
        return currentNumber > endNumber;
    }
}
