# Taiwan Invoice Tax System — iDempiere Plugin

## Project Overview

This is an **iDempiere 12.0 OSGi plugin** implementing Taiwan's Unified Invoice (統一發票) and Business Tax (營業稅) management system. It must comply with ROC Tax Act, VAT Act, and Ministry of Finance invoice regulations.

## Build & Test

```bash
# Compile only
mvn compile

# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=MInvoicePrefixTest

# Package
mvn package
```

## File Structure

```
tw.idempiere.invoice.tax/
├── META-INF/MANIFEST.MF          ← OSGi bundle manifest
├── OSGI-INF/component.xml        ← DS service registrations
├── pom.xml                       ← Maven build
├── src/tw/idempiere/invoice/tax/
│   ├── TaiwanInvoiceTaxActivator.java   ← Bundle lifecycle + PackIn
│   ├── model/
│   │   ├── TaiwanModelFactory.java      ← OSGi IModelFactory service (MUST EXIST)
│   │   ├── MInvoicePrefix.java          ← 字軌管理
│   │   ├── MInvoicePrefixMap.java       ← 發票字軌對應
│   │   ├── MInvoiceAdjustment.java      ← 進項折讓
│   │   └── MTaxStatement.java           ← 401 申報表
│   ├── service/
│   │   ├── InvoiceNumberingService.java
│   │   ├── TaxCalculationService.java
│   │   ├── MixedBusinessService.java
│   │   └── InvoiceValidationService.java
│   ├── callout/
│   │   ├── InvoicePrefixCallout.java
│   │   └── TaxStatementCallout.java
│   ├── process/
│   │   ├── GenerateTaxStatementProcess.java
│   │   └── ExportTaxReportProcess.java
│   └── util/
│       └── AccountingCodes.java
├── resources/
│   ├── 2pack/
│   │   └── tw_invoice_system.zip        ← PackOut.xml packed as ZIP
│   └── sql/                             ← 5 SQL scripts (reference)
└── test/tw/idempiere/invoice/tax/
    ├── model/*Test.java
    └── service/*Test.java
```

---

## Critical iDempiere Rules

### 1. Every Model MUST have `@Model` annotation

```java
import org.adempiere.base.Model;

@Model(table = MInvoicePrefix.Table_Name)
public class MInvoicePrefix extends PO { ... }
```

Without this, `AnnotationBasedModelFactory` cannot discover the class. All `TW_*` tables fall back to `GenericPO` and `beforeSave()`/`afterSave()` never run.

### 2. `initPO()` MUST NOT return null

```java
// WRONG — crashes with IllegalArgumentException in PO constructor
@Override
protected POInfo initPO(Properties ctx) {
    if (Table_ID <= 0) return null;  // ← NEVER DO THIS
    ...
}

// CORRECT — use Table_Name lookup
@Override
protected POInfo initPO(Properties ctx) {
    return POInfo.getPOInfo(ctx, Table_Name);
}
```

### 3. `TaiwanModelFactory` OSGi service MUST be registered

```java
@Component(immediate = true, service = IModelFactory.class,
           property = {"service.ranking:Integer=10"})
public class TaiwanModelFactory extends AnnotationBasedModelFactory {
    @Override
    protected String[] getPackages() {
        return new String[]{"tw.idempiere.invoice.tax.model"};
    }
}
```

Also register in `OSGI-INF/component.xml` and ensure `org.adempiere.base` is in `Import-Package` of `MANIFEST.MF`.

### 4. 2Pack ZIP structure

```
tw_invoice_system.zip
└── tw_invoice_system/
    └── dict/
        └── PackOut.xml
```

Activated via `Activator.start()` → `PackIn.importXML(zipPath, ctx, null)`.
**Do not** use `PackInProcess` directly — it is a `SvrProcess` subclass and cannot be instantiated directly.

### 5. MANIFEST.MF must import `org.adempiere.pipo2`

```
Import-Package: ...,
 org.adempiere.pipo2;version="[12.0.0,13.0.0)"
```

Without this, OSGi refuses to resolve `PackIn` and the bundle fails to start.

### 6. PackOut.xml format rules

- Root element: `<idempiere Name="..." Version="..." ...>`
- Boolean: `Y`/`N` (except `IsActive` uses `true`/`false` in some elements — follow existing iDempiere exports)
- FK references: `<AD_Reference_ID reference="id" reference-key="AD_Reference">13</AD_Reference_ID>`
- Every record needs a stable UUID (`AD_Table_UU`, `AD_Column_UU`, etc.)
- Order: AD_EntityType → AD_Reference → AD_Table → AD_Column → AD_Window → AD_Tab → AD_Field → AD_Menu
- `ColumnName` in XML **must exactly match** `COLUMNNAME_*` constants in Java model

### 7. `Table_ID` in Model classes

`Table_ID` starts at `0`. It is populated dynamically by `MTable.getTable(ctx, Table_Name)` after 2Pack installs the dictionary. Never hardcode a real integer — the value changes per iDempiere installation.

---

## Taiwan Invoice Domain Rules

| Rule | Detail |
|------|--------|
| 三聯式 (B2B) | Buyer has tax ID → 3-part invoice → `SaleAmount × 1.05 = GrossAmount` |
| 二聯式 (B2C) | No tax ID → 2-part invoice → `floor(GrossAmount / 1.05) = SaleAmount` |
| 稅額計算 | Always `FLOOR`, never `ROUND`. Use `BigDecimal + RoundingMode.FLOOR` |
| 申報期間 | Bimonthly: period = `(month - 1) / 2 + 1`. Period 1 = Jan-Feb ... Period 6 = Nov-Dec |
| 字軌狀態 | `I` (Inactive) → `A` (Active) → `C` (Complete). `C` cannot revert to `A` |
| 進項折讓 | Must be reported in same bimonthly period as the adjustment date |
| 進項稅期限 | 10-year expiry; warn 90 days before expiry |
| 兼營調整 | < 9 months operating → no adjustment required; ratio = taxable / total revenue |
| 發票日期 | Cannot be future date; same-prefix dates should increase (warn, not block) |
| 發票月份 | Should match transaction month (warn, not block) |

---

## Code Patterns

### Model accessor pattern (getters/setters)

```java
public String getPrefixCode() {
    return (String) get_Value(COLUMNNAME_PrefixCode);
}
public void setPrefixCode(String PrefixCode) {
    set_Value(COLUMNNAME_PrefixCode, PrefixCode);
}
```

### Boolean columns — use String Y/N

```java
public boolean isActive() {
    Object oo = get_Value(COLUMNNAME_IsActive);
    if (oo != null && oo instanceof Boolean)
        return (Boolean) oo;
    return "Y".equals(oo);
}
```

### Service layer — pure Java, no iDempiere dependency

`InvoiceNumberingService`, `TaxCalculationService`, `MixedBusinessService`, `InvoiceValidationService` are **pure Java** classes. They must be fully testable with JUnit without an iDempiere runtime.

### Validation result pattern

```java
public class ValidationResult {
    private final boolean valid;
    private final List<String> warnings;
    private final String errorMessage;
    // ...
}
```

---

## What NOT to Do

- **Never** return `null` from `initPO()`
- **Never** hardcode `Table_ID` as a non-zero integer
- **Never** use `PackInProcess` directly (it's a `SvrProcess`)
- **Never** import `org.adempiere.pipo2` in Java without declaring it in `MANIFEST.MF`
- **Never** use `Math.round()` for tax calculation — always `BigDecimal + FLOOR`
- **Never** block saves for date-sequence warnings — warn only
- **Never** use `float`/`double` for monetary amounts — always `BigDecimal`
