package tw.idempiere.invoice.tax.process;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        BigDecimal gross = grandTotal.setScale(0, RoundingMode.HALF_UP);
        return new AmountInfo(gross, BigDecimal.ZERO, BigDecimal.ZERO,
                "1", TAX_RATE, BigDecimal.ZERO, gross);
    }

    /**
     * Allocate an integer gross total across lines in proportion to each line's net,
     * so the returned integer amounts sum EXACTLY to grossTotal (last line absorbs rounding).
     */
    public static List<BigDecimal> allocateGross(List<BigDecimal> lineNets, BigDecimal grossTotal) {
        int n = lineNets.size();
        List<BigDecimal> result = new ArrayList<>();
        if (n == 0) return result;
        if (n == 1) {
            result.add(grossTotal.setScale(0, RoundingMode.HALF_UP));
            return result;
        }

        BigDecimal sumNet = BigDecimal.ZERO;
        for (BigDecimal net : lineNets) sumNet = sumNet.add(net);

        BigDecimal runningSum = BigDecimal.ZERO;
        for (int i = 0; i < n - 1; i++) {
            BigDecimal amt = sumNet.signum() == 0
                    ? BigDecimal.ZERO
                    : lineNets.get(i).multiply(grossTotal).divide(sumNet, 0, RoundingMode.HALF_UP);
            result.add(amt);
            runningSum = runningSum.add(amt);
        }
        result.add(grossTotal.subtract(runningSum).setScale(0, RoundingMode.HALF_UP));
        return result;
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

            MInvoiceLine[] lines = inv.getLines();
            List<BigDecimal> nets = new ArrayList<>();
            for (MInvoiceLine ln : lines) nets.add(ln.getLineNetAmt());

            BigDecimal grossTotal = inv.getGrandTotal().setScale(0, RoundingMode.HALF_UP);
            List<BigDecimal> grossAmounts = allocateGross(nets, grossTotal);

            List<EInvoiceItem> items = new ArrayList<>();
            int seq = 1;
            for (int i = 0; i < lines.length; i++) {
                MInvoiceLine ln = lines[i];
                String desc = ln.getName() != null ? ln.getName() : ln.getDescription();
                BigDecimal amt = grossAmounts.get(i);
                BigDecimal qty = ln.getQtyInvoiced().setScale(0, RoundingMode.HALF_UP);
                BigDecimal unitPrice = qty.compareTo(BigDecimal.ONE) == 0
                        ? amt
                        : (qty.signum() == 0 ? BigDecimal.ZERO : amt.divide(qty, 0, RoundingMode.HALF_UP));
                items.add(new EInvoiceItem(desc, qty, unitPrice,
                        "1", amt, String.format("%03d", seq++), null, null));
            }

            AmountInfo amount = b2cAmount(grossTotal);
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
