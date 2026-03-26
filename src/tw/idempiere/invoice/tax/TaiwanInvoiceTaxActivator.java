package tw.idempiere.invoice.tax;

import org.adempiere.plugin.utils.Incremental2PackActivator;
import org.compiere.util.DB;
import org.compiere.util.Trx;

import java.util.logging.Level;

/**
 * OSGi Bundle Activator for the Taiwan Invoice Tax System.
 *
 * Extends {@link Incremental2PackActivator} which automatically:
 * <ul>
 *   <li>Waits for IDictionaryService and framework readiness before installing</li>
 *   <li>Loads 2Pack from {@code /META-INF/2Pack_*.zip} inside the bundle</li>
 *   <li>Checks AD_Package_Imp for idempotency (skips if already installed)</li>
 *   <li>Tracks package versions for incremental upgrades</li>
 * </ul>
 *
 * After 2Pack installs, {@link #afterPackIn()} grants all active roles
 * read-write access to the 4 TW windows (EntityType='TW').
 */
public class TaiwanInvoiceTaxActivator extends Incremental2PackActivator {

    /**
     * Called by Incremental2PackActivator after each successful 2Pack install.
     * Grants IsReadWrite=Y access to all 4 TW windows for every active Role.
     * Idempotent — skips rows that already exist.
     */
    @Override
    protected void afterPackIn() {
        String trxName = Trx.createTrxName("TW_WindowAccess");
        Trx trx = Trx.get(trxName, true);
        try {
            int inserted = DB.executeUpdate(
                "INSERT INTO AD_Window_Access "
                + "  (AD_Window_ID, AD_Role_ID, AD_Client_ID, AD_Org_ID, "
                + "   IsActive, Created, CreatedBy, Updated, UpdatedBy, "
                + "   IsReadWrite, AD_Window_Access_UU) "
                + "SELECT w.AD_Window_ID, r.AD_Role_ID, r.AD_Client_ID, 0, "
                + "       'Y', NOW(), 100, NOW(), 100, "
                + "       'Y', gen_random_uuid() "
                + "FROM AD_Window w "
                + "CROSS JOIN AD_Role r "
                + "WHERE w.EntityType = 'TW' "
                + "  AND r.IsActive   = 'Y' "
                + "  AND NOT EXISTS ( "
                + "    SELECT 1 FROM AD_Window_Access x "
                + "    WHERE x.AD_Window_ID = w.AD_Window_ID "
                + "      AND x.AD_Role_ID   = r.AD_Role_ID "
                + "  )",
                trxName);
            trx.commit();
            if (logger.isLoggable(Level.INFO))
                logger.info("TW Window Access: granted " + inserted
                        + " role-window combinations (EntityType=TW)"
                        + " — users must re-login to see new permissions");
        } catch (Exception e) {
            trx.rollback();
            logger.log(Level.WARNING, "TW Window Access: failed to grant role access", e);
        } finally {
            trx.close();
        }
    }
}
