package tw.idempiere.invoice.tax.einvoice;

import static org.junit.Assert.*;
import org.junit.Test;

public class RandomNumberGeneratorTest {

    @Test
    public void zeroPadsToFourDigits() {
        assertEquals("0007", RandomNumberGenerator.generate(() -> 7));
    }

    @Test
    public void takesModulo10000() {
        assertEquals("2345", RandomNumberGenerator.generate(() -> 12345));
    }

    @Test
    public void defaultGenerateIsFourDigits() {
        String r = RandomNumberGenerator.generate();
        assertTrue(r.matches("\\d{4}"));
    }
}
