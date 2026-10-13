package tw.idempiere.invoice.tax.einvoice;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/** Generates the 4-digit random number (隨機碼) required by MIG F0401. */
public final class RandomNumberGenerator {

    private RandomNumberGenerator() {}

    public static String generate(IntSupplier source) {
        int n = Math.floorMod(source.getAsInt(), 10000);
        return String.format("%04d", n);
    }

    public static String generate() {
        return generate(() -> ThreadLocalRandom.current().nextInt(10000));
    }
}
