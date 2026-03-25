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

## Schema Documentation

Table schemas and PackOut.xml writing guides are in `docs/schema/`:

- `docs/schema/table-definitions.md` — all 4 TW_* tables: columns, types, business rules
- `docs/schema/packout-column-reference.md` — AD_Reference_ID lookup table + XML templates

**⚠️ No SQL files exist in this project.** 2Pack (PackOut.xml) creates physical tables automatically via iDempiere's ColumnElementHandler DDL. Never add SQL DDL scripts — they will not be executed and could cause confusion.

---

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
│   └── (no sql/ — 2Pack handles DDL)
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

### 2. `initPO()` MUST use `MTable.getTable_ID()` lookup

```java
// WRONG — POInfo.getPOInfo(ctx, String tableName) overload does NOT exist
@Override
protected POInfo initPO(Properties ctx) {
    return POInfo.getPOInfo(ctx, Table_Name);  // ← COMPILE ERROR, no such overload
}

// WRONG — crashes with IllegalArgumentException in PO constructor
@Override
protected POInfo initPO(Properties ctx) {
    if (Table_ID <= 0) return null;  // ← NEVER DO THIS
    ...
}

// CORRECT — use MTable.getTable_ID() to look up the integer ID first
@Override
protected POInfo initPO(Properties ctx) {
    int tableId = MTable.getTable_ID(Table_Name);
    if (tableId <= 0) {
        // Table not yet installed via 2Pack — expected on first startup before PackIn
        return null;
    }
    return POInfo.getPOInfo(ctx, tableId, get_TrxName());
}
```

Note: `MTable.getTable_ID()` returns a valid value only after 2Pack installs the dictionary. The safe execution order is: bundle start → Activator calls `PackIn.importXML()` → tables registered → Model classes used. Returning `null` when `tableId <= 0` is safe because the PO constructor handles this gracefully at that early stage.

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

Also register in a dedicated `OSGI-INF/TaiwanModelFactory.xml` (not the same file as other components) and ensure `org.adempiere.base` is in `Import-Package` of `MANIFEST.MF`.

**Files needed:**
- `OSGI-INF/component.xml` — existing Activator component (keep as-is, but do not re-register Activator as a DS component if it is already a `BundleActivator`)
- `OSGI-INF/TaiwanModelFactory.xml` — new file for the ModelFactory DS component

**MANIFEST.MF must use wildcard:**
```
Service-Component: OSGI-INF/*.xml
```

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
| 二聯式 (B2C) | No tax ID → 2-part invoice → `floor(GrossAmount / 1.05) = SaleAmount`; 稅額 = `floor(SaleAmount × 0.05)` (NOT `GrossAmount - SaleAmount` — differs by 1 yuan at boundary values; use the former per Ministry of Finance rules) |
| 稅額計算 | Always `FLOOR`, never `ROUND`. Use `BigDecimal + RoundingMode.FLOOR` |
| 申報期間 | Bimonthly: period = `(month - 1) / 2 + 1`. Period 1 = Jan-Feb ... Period 6 = Nov-Dec |
| 字軌狀態 | `I` (Inactive) → `A` (Active) → `C` (Complete). `C` cannot revert to `A` |
| 進項折讓 | 必須當期申報（超期需使用者確認稅務風險，非單純 warning — 有補稅/裁罰風險） |
| 進項稅期限 | 10-year expiry; warn 90 days before expiry |
| 兼營調整 | < 9 months operating → no adjustment required; ratio = taxable / total revenue |
| 發票日期 | Cannot be future date; same-prefix dates should increase (warn, not block) |
| 發票月份 | Should match transaction month (warn, not block) |

## Required Fields by Law

| Table | Field | Rule |
|-------|-------|------|
| TW_InvoicePrefix | PrefixStartDate, PrefixEndDate | 字軌有效期間（財政部核配，2個月一期），開立發票時需驗證 DateInvoiced BETWEEN PrefixStartDate AND PrefixEndDate |
| TW_Invoice_Prefix_Map | BuyerTaxID CHAR(8) | 三聯式（B2B）法定必填買方統一編號；SALES_TRIPART 時不可為空 |
| TW_InvoiceAdjustment | AdjustmentDirection | 需區分方向：SALES（銷項折讓，我方開立折讓單）vs PURCHASE（進項折讓，供應商開立折讓單給我方） |
| TW_TaxStatement | ZeroRateSalesAmount | 零稅率銷售額（出口），需獨立申報，可申請退稅，非免稅 |
| TW_TaxStatement | CarryOverTaxCredit | 上期累積留抵稅額，跨期累積 |
| TW_TaxStatement | NonDeductibleInputTax | 不可扣抵進項稅額 |

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

- **Never** use `POInfo.getPOInfo(ctx, String tableName)` — that overload does not exist; use `MTable.getTable_ID()` first
- **Never** return `null` from `initPO()` unless `MTable.getTable_ID()` returns `<= 0` (before 2Pack installs)
- **Never** hardcode `Table_ID` as a non-zero integer
- **Never** put two `<scr:component>` root elements in one XML file — each OSGi DS component needs its own file
- `Service-Component` in `MANIFEST.MF` should be `OSGI-INF/*.xml` (wildcard) to auto-discover all component files
- **Never** use `PackInProcess` directly (it's a `SvrProcess`)
- **Never** import `org.adempiere.pipo2` in Java without declaring it in `MANIFEST.MF`
- **Never** use `Math.round()` for tax calculation — always `BigDecimal + FLOOR`
- **Never** block saves for date-sequence warnings — warn only
- **Never** use `float`/`double` for monetary amounts — always `BigDecimal`
