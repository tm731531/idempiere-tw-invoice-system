package tw.idempiere.invoice.tax.model;

import static org.junit.Assert.*;
import org.junit.Test;

public class MEInvoiceMessageTest {
    @Test public void tableName() {
        assertEquals("TW_EInvoiceMessage", MEInvoiceMessage.Table_Name);
    }
    @Test public void columnConstants() {
        assertEquals("RandomNumber", MEInvoiceMessage.COLUMNNAME_RandomNumber);
        assertEquals("Status", MEInvoiceMessage.COLUMNNAME_Status);
        assertEquals("MessageType", MEInvoiceMessage.COLUMNNAME_MessageType);
        assertEquals("TW_Invoice_Prefix_Map_ID", MEInvoiceMessage.COLUMNNAME_TW_Invoice_Prefix_Map_ID);
    }
}
