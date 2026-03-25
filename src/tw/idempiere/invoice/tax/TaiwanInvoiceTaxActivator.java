package tw.idempiere.invoice.tax;

import org.adempiere.plugin.utils.Incremental2PackActivator;

/**
 * OSGi Bundle Activator for the Taiwan Invoice Tax System.
 *
 * Extends {@link Incremental2PackActivator} which automatically:
 * <ul>
 *   <li>Waits for IDictionaryService and framework readiness before installing</li>
 *   <li>Loads 2Pack from {@code /META-INF/2Pack_1.0.0.zip} inside the bundle</li>
 *   <li>Checks AD_Package_Imp for idempotency (skips if already installed)</li>
 *   <li>Tracks package versions for incremental upgrades</li>
 * </ul>
 *
 * No overrides needed — all lifecycle and PackIn handling is in the base class.
 */
public class TaiwanInvoiceTaxActivator extends Incremental2PackActivator {
    // Incremental2PackActivator handles everything:
    // - Loads /META-INF/2Pack_<version>.zip automatically
    // - Checks AD_Package_Imp for idempotency
    // - Supports incremental version upgrades
}
