package tw.idempiere.invoice.tax.einvoice;

import static org.junit.Assert.*;
import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.Test;

public class MigF0401BuilderTest {

    private static String norm(String s) { return s.replaceAll(">\\s+<", "><").trim(); }

    private EInvoiceDoc officialPrintSampleDoc() {
        SellerInfo seller = new SellerInfo("90000011", "測試公司", "台北市中華路1段123號",
                "王小明", "0212345678", "0212345678", "test1@test.com.tw", "A123456789", "defaultUser");
        BuyerInfo buyer = new BuyerInfo("0000000000", "測試買家", "台中市中正路888號");
        EInvoiceItem item = new EInvoiceItem("純喫茶紅茶", new BigDecimal("1"), new BigDecimal("20"),
                "1", new BigDecimal("20"), "001", "remark", "A123456789");
        AmountInfo amount = new AmountInfo(new BigDecimal("20"), new BigDecimal("0"), new BigDecimal("0"),
                "1", new BigDecimal("0.05"), new BigDecimal("0"), new BigDecimal("20"));
        return EInvoiceDoc.forB2CPrint("KZ10000012", LocalDateTime.of(2024, 12, 1, 9, 0, 0),
                seller, buyer, "07", Collections.singletonList(item), amount, "1234");
    }

    @Test
    public void reproducesOfficialPrintSample() throws Exception {
        String expected = new String(Files.readAllBytes(
                new File("test/resources/mig/F0401-B2C-print-sample.xml").toPath()), StandardCharsets.UTF_8);
        String actual = MigF0401Builder.build(officialPrintSampleDoc());
        assertEquals(norm(expected), norm(actual));
    }
}
