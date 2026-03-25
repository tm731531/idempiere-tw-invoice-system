package tw.idempiere.invoice.tax;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.adempiere.pipo2.PackIn;
import org.compiere.model.MTable;
import org.compiere.util.Env;
import org.compiere.util.Trx;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * OSGi Bundle Activator for the Taiwan Invoice Tax System.
 *
 * On bundle start, installs the 2Pack dictionary (PackOut.xml) so that
 * TW_* tables and AD metadata are registered in the iDempiere system.
 * The install is idempotent: if the tables already exist, PackIn is skipped.
 */
public class TaiwanInvoiceTaxActivator implements BundleActivator {

    private static final Logger log = Logger.getLogger(TaiwanInvoiceTaxActivator.class.getName());

    private BundleContext bundleContext;

    @Override
    public void start(BundleContext context) throws Exception {
        bundleContext = context;
        log.info("=== Taiwan Invoice Tax System Bundle Starting ===");
        installDictionary(context);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        log.info("=== Taiwan Invoice Tax System Bundle Stopping ===");
        bundleContext = null;
    }

    private void installDictionary(BundleContext context) {
        try {
            // Idempotency: skip if already installed
            if (MTable.getTable_ID("TW_InvoicePrefix") > 0) {
                log.info("Taiwan Invoice Tax System 2Pack already installed — skipping.");
                return;
            }
            URL zipUrl = context.getBundle().getResource("2pack/tw_invoice_system.zip");
            if (zipUrl == null) {
                log.warning("2Pack ZIP not found at 2pack/tw_invoice_system.zip — skipping dictionary install");
                return;
            }
            File tempZip = File.createTempFile("tw_invoice_system", ".zip");
            try (InputStream in = zipUrl.openStream();
                 FileOutputStream out = new FileOutputStream(tempZip)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            String trxName = Trx.createTrxName("TwInvoicePackIn");
            Trx trx = Trx.get(trxName, true);
            try {
                PackIn packIn = new PackIn();
                packIn.importXML(tempZip.getAbsolutePath(), Env.getCtx(), trxName);
                trx.commit();
                log.info("Taiwan Invoice Tax System 2Pack installed successfully.");
            } catch (Exception e) {
                trx.rollback();
                throw e;
            } finally {
                trx.close();
                tempZip.delete();
            }
        } catch (Exception e) {
            log.log(Level.WARNING,
                "2Pack install failed (non-fatal, tables may already exist): " + e.getMessage());
        }
    }
}
