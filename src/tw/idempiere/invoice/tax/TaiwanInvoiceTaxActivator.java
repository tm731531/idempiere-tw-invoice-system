package tw.idempiere.invoice.tax;

import org.adempiere.plugin.utils.AdempiereActivator;

/**
 * OSGi Bundle Activator for the Taiwan Invoice Tax System.
 *
 * Extends {@link AdempiereActivator} which automatically:
 * <ul>
 *   <li>Waits for IDictionaryService and framework readiness before installing</li>
 *   <li>Loads 2Pack from {@code /META-INF/2Pack.zip} inside the bundle</li>
 *   <li>Checks AD_Package_Imp for idempotency (skips if already installed)</li>
 *   <li>Acquires a DB lock to prevent concurrent installs</li>
 * </ul>
 *
 * No overrides needed — getName() and getVersion() read from bundle headers,
 * and the default packIn() + install() behavior is sufficient.
 */
public class TaiwanInvoiceTaxActivator extends AdempiereActivator {
    // AdempiereActivator handles everything:
    // - getName() reads Bundle-SymbolicName
    // - getVersion() reads Bundle-Version
    // - packIn() loads /META-INF/2Pack.zip
    // - installPackage() checks AD_Package_Imp for idempotency
}
