package tw.idempiere.invoice.tax.process;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MOrg;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MSysConfig;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.Env;
import tw.idempiere.invoice.tax.einvoice.AmountInfo;
import tw.idempiere.invoice.tax.einvoice.BuyerInfo;
import tw.idempiere.invoice.tax.einvoice.EInvoiceDoc;
import tw.idempiere.invoice.tax.einvoice.EInvoiceItem;
import tw.idempiere.invoice.tax.einvoice.MigF0401Builder;
import tw.idempiere.invoice.tax.einvoice.RandomNumberGenerator;
import tw.idempiere.invoice.tax.einvoice.SellerInfo;
import tw.idempiere.invoice.tax.einvoice.TurnkeyFileAdapter;
import tw.idempiere.invoice.tax.model.MEInvoiceMessage;
import tw.idempiere.invoice.tax.model.MInvoicePrefixMap;

/**
 * Generates a MIG 4.1 F0401 (B2C 二聯式, 列印紙本) e-invoice XML from an issued
 * invoice, drops it into Turnkey's intake directory, and records the attempt
 * in TW_EInvoiceMessage.
 */
public class GenerateEInvoiceProcess extends SvrProcess {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.05");

    /** B2C 二聯式 F0401: SalesAmount=TotalAmount=gross, TaxAmount=0 (no split). */
    public static AmountInfo b2cAmount(BigDecimal grandTotal) {
        return new AmountInfo(grandTotal, BigDecimal.ZERO, BigDecimal.ZERO,
                "1", TAX_RATE, BigDecimal.ZERO, grandTotal);
    }

    private int p_MapId;

    @Override protected void prepare() {
        for (ProcessInfoParameter para : getParameter()) {
            if ("TW_Invoice_Prefix_Map_ID".equals(para.getParameterName())) {
                Object val = para.getParameter();
                if (val instanceof String)
                    p_MapId = ((String) val).trim().isEmpty() ? 0 : Integer.parseInt(((String) val).trim());
                else if (val instanceof Number)
                    p_MapId = ((Number) val).intValue();
                else
                    p_MapId = para.getParameterAsInt();
            }
        }
        if (p_MapId <= 0)
            p_MapId = getRecord_ID();
    }

    @Override protected String doIt() throws Exception {
        MInvoicePrefixMap map = new MInvoicePrefixMap(getCtx(), p_MapId, get_TrxName());
        if (map.get_ID() <= 0) throw new AdempiereException("Invoice map record not found");

        // Auto-commit (null trxName): the audit row must survive a rollback of
        // this process's own transaction when doIt() rethrows (see brain:
        // po-model best-effort audit pattern). Both the WRITTEN and FAILED
        // saves below commit independently of get_TrxName().
        MEInvoiceMessage msg = new MEInvoiceMessage(getCtx(), 0, null);
        msg.setTW_Invoice_Prefix_Map_ID(map.get_ID());
        msg.setMessageType("F0401");
        msg.setMigVersion("4.1");
        msg.setGeneratedOn(new java.sql.Timestamp(System.currentTimeMillis()));
        msg.setStatus("DRAFT");

        try {
            MInvoice inv = new MInvoice(getCtx(), map.getC_Invoice_ID(), get_TrxName());
            MOrgInfo orgInfo = MOrgInfo.get(getCtx(), inv.getAD_Org_ID(), get_TrxName());
            MOrg org = MOrg.get(getCtx(), inv.getAD_Org_ID());
            String sellerTaxId = orgInfo.getTaxID();
            if (sellerTaxId == null || sellerTaxId.trim().isEmpty())
                throw new AdempiereException("Seller tax id (賣方統編) not set on Org Info");

            // Skeleton: populate identifier + name. Seller Name/Address are always
            // emitted by the builder via `el` (may render as empty elements when
            // null); only the optional contact fields use `elOpt` and are omitted.
            // Populating Seller address from the Org location is a deferred
            // deploy-time item.
            SellerInfo seller = new SellerInfo(sellerTaxId, org.getName(),
                    null, null, null, null, null, null, null);
            String buyerId = (map.getBuyerTaxID() == null || map.getBuyerTaxID().trim().isEmpty())
                    ? "0000000000" : map.getBuyerTaxID().trim();
            BuyerInfo buyer = new BuyerInfo(buyerId, null, null);

            List<EInvoiceItem> items = new ArrayList<>();
            int seq = 1;
            for (MInvoiceLine ln : inv.getLines()) {
                String desc = ln.getName() != null ? ln.getName() : ln.getDescription();
                items.add(new EInvoiceItem(desc, ln.getQtyInvoiced(), ln.getPriceActual(),
                        "1", ln.getLineNetAmt(), String.format("%03d", seq++), null, null));
            }

            AmountInfo amount = b2cAmount(inv.getGrandTotal());
            String random = RandomNumberGenerator.generate();
            EInvoiceDoc doc = EInvoiceDoc.forB2CPrint(map.getInvoiceNumber(),
                    map.getInvoiceDate().toLocalDateTime(), seller, buyer, "07", items, amount, random);
            String xml = MigF0401Builder.build(doc);

            msg.setRandomNumber(random);

            String dir = MSysConfig.getValue("TW_TURNKEY_INTAKE_DIR", "/opt/turnkey/B2C",
                    Env.getAD_Client_ID(getCtx()));
            String file = new TurnkeyFileAdapter(dir).submit(doc.getInvoiceNumber(), xml);
            msg.setXmlFileName(file);
            msg.setStatus("WRITTEN");
            msg.saveEx();
            return "F0401 written: " + file;
        } catch (Exception e) {
            msg.setStatus("FAILED");
            String errMsg = e.getMessage();
            if (errMsg != null && errMsg.length() > 2000)
                errMsg = errMsg.substring(0, 2000);
            msg.setErrorMessage(errMsg);
            try {
                msg.saveEx();
            } catch (Exception saveEx) {
                // Do not let an audit-save failure mask the original exception.
                log.severe("Failed to save FAILED e-invoice audit row: " + saveEx.getMessage());
            }
            throw e;
        }
    }
}
