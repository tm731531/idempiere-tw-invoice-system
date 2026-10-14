package tw.idempiere.invoice.tax.process;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import tw.idempiere.invoice.tax.einvoice.AmountInfo;

public class GenerateEInvoiceAmountsTest {

    @Test public void b2cDoesNotSplitTax() {
        AmountInfo a = GenerateEInvoiceProcess.b2cAmount(new BigDecimal("325"));
        assertEquals(new BigDecimal("325"), a.salesAmount);
        assertEquals(new BigDecimal("325"), a.totalAmount);
        assertEquals(a.salesAmount, a.totalAmount);
        assertEquals(0, a.salesAmount.scale());
        assertEquals(0, a.totalAmount.scale());
        assertEquals(BigDecimal.ZERO.setScale(0), a.taxAmount.setScale(0));
        assertEquals("1", a.taxType);
    }

    @Test public void allocateGrossEvenSplitWithRoundingAbsorbedByLastLine() {
        List<BigDecimal> nets = Arrays.asList(new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"));
        List<BigDecimal> result = GenerateEInvoiceProcess.allocateGross(nets, new BigDecimal("315"));

        assertEquals(Arrays.asList(new BigDecimal("105"), new BigDecimal("105"), new BigDecimal("105")), result);
        assertSumEquals(new BigDecimal("315"), result);
        assertAllScaleZero(result);
    }

    @Test public void allocateGrossUnevenNetsSumsExactly() {
        List<BigDecimal> nets = Arrays.asList(new BigDecimal("42.75"), new BigDecimal("47.5"), new BigDecimal("61.75"));
        List<BigDecimal> result = GenerateEInvoiceProcess.allocateGross(nets, new BigDecimal("161"));

        assertEquals(3, result.size());
        assertSumEquals(new BigDecimal("161"), result);
        assertAllScaleZero(result);
    }

    @Test public void allocateGrossSingleLine() {
        List<BigDecimal> result = GenerateEInvoiceProcess.allocateGross(Arrays.asList(new BigDecimal("10")), new BigDecimal("11"));
        assertEquals(Arrays.asList(new BigDecimal("11")), result);
    }

    @Test public void allocateGrossEmptyList() {
        List<BigDecimal> result = GenerateEInvoiceProcess.allocateGross(Arrays.asList(), BigDecimal.ZERO);
        assertTrue(result.isEmpty());
    }

    @Test public void allocateGrossZeroSumNetPutsWholeTotalOnLastLine() {
        List<BigDecimal> nets = Arrays.asList(BigDecimal.ZERO, BigDecimal.ZERO);
        List<BigDecimal> result = GenerateEInvoiceProcess.allocateGross(nets, new BigDecimal("50"));

        assertEquals(Arrays.asList(new BigDecimal("0"), new BigDecimal("50")), result);
        assertSumEquals(new BigDecimal("50"), result);
    }

    private static void assertSumEquals(BigDecimal expected, List<BigDecimal> amounts) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal amt : amounts) sum = sum.add(amt);
        assertEquals(expected.setScale(0), sum.setScale(0));
    }

    private static void assertAllScaleZero(List<BigDecimal> amounts) {
        for (BigDecimal amt : amounts) assertEquals(0, amt.scale());
    }
}
