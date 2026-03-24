/******************************************************************************
 * Taiwan Invoice Tax System for iDempiere
 * Copyright (C) Taiwan iDempiere Community. All Rights Reserved.
 * License: GNU General Public License version 2
 *****************************************************************************/
package tw.idempiere.invoice.tax;

import java.util.logging.Level;

import org.compiere.util.CLogger;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Taiwan Invoice Tax System - OSGi Bundle Activator
 * <p>
 * Manages the plugin lifecycle for the Taiwan Unified Invoice
 * and Business Tax Management module.
 * </p>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 */
public class TaiwanInvoiceTaxActivator implements BundleActivator {

    /** Logger */
    private static final CLogger log = CLogger.getCLogger(TaiwanInvoiceTaxActivator.class);

    /** Bundle plugin ID */
    public static final String PLUGIN_ID = "tw.idempiere.invoice.tax";

    /** Bundle context reference */
    private static BundleContext bundleContext;

    /**
     * Called by OSGi framework when bundle starts.
     *
     * @param context OSGi BundleContext
     * @throws Exception if startup fails
     */
    @Override
    public void start(BundleContext context) throws Exception {
        bundleContext = context;
        log.info("=== Taiwan Invoice Tax System Bundle Started ===");
        System.out.println("=== Taiwan Invoice Tax System Bundle Started (version "
                + context.getBundle().getVersion() + ") ===");
    }

    /**
     * Called by OSGi framework when bundle stops.
     *
     * @param context OSGi BundleContext
     * @throws Exception if shutdown fails
     */
    @Override
    public void stop(BundleContext context) throws Exception {
        log.info("Taiwan Invoice Tax System Bundle Stopping...");
        try {
            // Cleanup resources if needed
            bundleContext = null;
            log.info("Taiwan Invoice Tax System Bundle Stopped.");
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error stopping Taiwan Invoice Tax System bundle", e);
        }
    }

    /**
     * DS activate method - called by Declarative Services.
     */
    public void activate() {
        log.info("Taiwan Invoice Tax System DS Component Activated.");
    }

    /**
     * DS deactivate method - called by Declarative Services.
     */
    public void deactivate() {
        log.info("Taiwan Invoice Tax System DS Component Deactivated.");
    }

    /**
     * Returns the current BundleContext.
     *
     * @return BundleContext or null if bundle is not active
     */
    public static BundleContext getBundleContext() {
        return bundleContext;
    }
}
