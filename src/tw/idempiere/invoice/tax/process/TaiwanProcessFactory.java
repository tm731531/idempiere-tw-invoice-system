package tw.idempiere.invoice.tax.process;

import org.adempiere.base.IProcessFactory;
import org.compiere.process.ProcessCall;
import org.osgi.service.component.annotations.Component;

/**
 * OSGi DS component that registers TW SvrProcess classes with iDempiere's
 * DefaultProcessFactory. Without this, the core bundle's classloader cannot
 * find classes in this plugin bundle and throws ClassNotFoundException.
 */
@Component(immediate = true, service = IProcessFactory.class)
public class TaiwanProcessFactory implements IProcessFactory {

    @Override
    public ProcessCall newProcessInstance(String className) {
        if (GenerateTaxStatementProcess.class.getName().equals(className))
            return new GenerateTaxStatementProcess();
        if (ExportTaxReportProcess.class.getName().equals(className))
            return new ExportTaxReportProcess();
        return null;
    }
}
