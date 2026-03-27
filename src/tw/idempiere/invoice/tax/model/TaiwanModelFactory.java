package tw.idempiere.invoice.tax.model;

import org.adempiere.base.AnnotationBasedModelFactory;
import org.adempiere.base.IModelFactory;
import org.osgi.service.component.annotations.Component;

/**
 * OSGi DS ModelFactory for the Taiwan Invoice Tax System.
 *
 * Registers all @Model-annotated PO classes in the model package so that
 * iDempiere's AnnotationBasedModelFactory can discover them.
 * Without this, TW_* tables fall back to GenericPO and beforeSave()/afterSave() never run.
 */
// NOTE: @Component annotation is present for documentation only.
// The build does not include bnd/felix-scr annotation processing, so
// DS registration is controlled exclusively by OSGI-INF/TaiwanModelFactory.xml.
// Do not remove the XML file assuming the annotation handles registration.
@Component(
    immediate = true,
    service = IModelFactory.class,
    property = {"service.ranking:Integer=10"}
)
public class TaiwanModelFactory extends AnnotationBasedModelFactory {

    @Override
    protected String[] getPackages() {
        return new String[]{"tw.idempiere.invoice.tax.model"};
    }
}
