package tw.idempiere.invoice.tax;

import org.adempiere.plugin.utils.Incremental2PackActivator;
import org.compiere.util.DB;

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
        // Grant window access to all active roles
        String windowSql =
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
            + "  )";
        logger.info("[TW] afterPackIn() firing — inserting AD_Window_Access");
        int windowsInserted = DB.executeUpdate(windowSql, (String) null);
        logger.info("[TW] afterPackIn() window access inserted=" + windowsInserted);

        // Grant process access to all active roles
        String processSql =
            "INSERT INTO AD_Process_Access "
            + "  (AD_Process_ID, AD_Role_ID, AD_Client_ID, AD_Org_ID, "
            + "   IsActive, Created, CreatedBy, Updated, UpdatedBy, "
            + "   IsReadWrite) "
            + "SELECT p.AD_Process_ID, r.AD_Role_ID, r.AD_Client_ID, 0, "
            + "       'Y', NOW(), 100, NOW(), 100, 'Y' "
            + "FROM AD_Process p "
            + "CROSS JOIN AD_Role r "
            + "WHERE p.EntityType = 'TW' "
            + "  AND r.IsActive   = 'Y' "
            + "  AND NOT EXISTS ( "
            + "    SELECT 1 FROM AD_Process_Access x "
            + "    WHERE x.AD_Process_ID = p.AD_Process_ID "
            + "      AND x.AD_Role_ID    = r.AD_Role_ID "
            + "  )";
        logger.info("[TW] afterPackIn() firing — inserting AD_Process_Access");
        int processesInserted = DB.executeUpdate(processSql, (String) null);
        logger.info("[TW] afterPackIn() process access inserted=" + processesInserted);

        if (windowsInserted >= 0 && processesInserted >= 0)
            logger.info("TW Access granted: " + windowsInserted + " window + "
                    + processesInserted + " process role combinations"
                    + " — users must re-login to see new permissions");
        else
            logger.warning("TW Access: some grants may have failed — "
                    + "window=" + windowsInserted + " process=" + processesInserted
                    + " — check DB logs");
    }
}
