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
├── OSGI-INF/                     ← One XML file per DS component (wildcard: OSGI-INF/*.xml)
│   ├── TaiwanModelFactory.xml
│   ├── InvoicePrefixEventHandler.xml
│   ├── InvoicePrefixMapEventHandler.xml
│   ├── InvoiceAdjustmentEventHandler.xml
│   └── TaxStatementEventHandler.xml
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
│   ├── callout/                         ← Event handlers (OSGi DS) + pure-static validators
│   │   ├── InvoicePrefixEventHandler.java    ← OSGi DS, registered in OSGI-INF/
│   │   ├── InvoicePrefixMapEventHandler.java ← OSGi DS, registered in OSGI-INF/
│   │   ├── InvoiceAdjustmentEventHandler.java← OSGi DS, registered in OSGI-INF/
│   │   ├── TaxStatementEventHandler.java     ← OSGi DS, registered in OSGI-INF/
│   │   ├── InvoicePrefixValidator.java       ← Static helpers only, NOT an OSGi service
│   │   ├── InvoicePrefixMapValidator.java    ← Static helpers only, NOT an OSGi service
│   │   ├── InvoiceAdjustmentValidator.java   ← Static helpers only, NOT an OSGi service
│   │   ├── TaxStatementValidator.java        ← Static helpers only, NOT an OSGi service
│   │   ├── InvoicePrefixCallout.java
│   │   └── TaxStatementCallout.java
│   ├── process/
│   │   ├── GenerateTaxStatementProcess.java  ← planned, not yet implemented
│   │   └── ExportTaxReportProcess.java       ← planned, not yet implemented
│   └── util/
│       └── AccountingCodes.java
├── resources/
│   ├── 2pack/
│   │   └── (ZIP generated at build time — do not commit stale ZIPs)
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

### 8. Standard AD_Field set per tab (verified from live DB)

Every tab must include this full set of standard fields — verified against Pack Out and Tax Rate windows:

| Column | SeqNo | IsDisplayed | IsReadOnly | Note |
|--------|-------|-------------|------------|------|
| PK_ID (e.g. TW_InvoicePrefix_ID) | 0 | N | — | Hidden |
| _UU (e.g. TW_InvoicePrefix_UU) | 0 | N | — | Hidden |
| Created | 0 | N | — | Hidden |
| CreatedBy | 0 | N | — | Hidden |
| Updated | 0 | N | — | Hidden |
| UpdatedBy | 0 | N | — | Hidden |
| AD_Client_ID (廠商) | 1 | Y | Y | **Required**: `dataNew()` uses this to init env context |
| AD_Org_ID (組織) | 2 | Y | N | **Required**: without it `MRole.canUpdate()` returns false → all fields gray |
| IsActive (啟用) | 3 | Y | N | Displayed |

**`_UU` column must have `IsUpdateable=Y`** — the PO framework writes the UUID on save. `IsUpdateable=N` causes `_UU` to stay NULL for all records (verified: standard tables C_Tax, C_BPartner both use `Y`).

**AD_Field UUID strategy for upgrades:** `AD_Field` has a `UNIQUE(ad_tab_id, ad_column_id)` constraint. When bumping 2Pack version, **keep existing field UUIDs** — changing them causes INSERT failure on upgrade. Only new fields (not previously in PackOut.xml) should get fresh uuid4 values.

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

## Known Validator Pattern

### 9. ModelValidator vs EventHandler — use EventHandler only

Do NOT implement `ModelValidator` interface in validator helper classes.
The correct pattern for this plugin is:

- `*Validator.java` — pure Java class with ONLY static helper methods, no interface
- `*EventHandler.java` — extends `AbstractEventHandler`, registered as OSGi DS component, calls static helpers
- `OSGI-INF/*.xml` — one XML file per EventHandler

**Example structure:**

```java
// CORRECT — static helpers only, no implements
public class InvoicePrefixValidator {
    public static String validatePrefixCode(String code) { ... }
    public static String validateStatusTransition(String from, String to) { ... }
}

// CORRECT — EventHandler wires OSGi event to static validator
@Component(immediate = true, service = IEventHandler.class, ...)
public class InvoicePrefixEventHandler extends AbstractEventHandler {
    @Override
    protected void doHandleEvent(Event event) {
        String topic = (String) event.getProperty(IEventTopics.EVENT_TOPIC);
        if (IEventTopics.PO_BEFORE_CHANGE.equals(topic)) {
            String err = InvoicePrefixValidator.validateStatusTransition(...);
            if (err != null) throw new AdempiereException(err);
        }
    }
}
```

Validator classes that implement `ModelValidator` but are never registered as OSGi services are dead code — the interface methods (`initialize`, `modelChange`, `docValidate`, `getAD_Client_ID`, `login`) will never be called.

---

### 10. AD_Process_Para in PackOut.xml — required fields

`ProcessParaElementHandler` does NOT inherit `AD_Process_ID` from the parent `<AD_Process>` context.
Both of the following fields are required or the save will fail silently with no SQL error until you check the PostgreSQL log:

```xml
<AD_Process_Para type="table">
  <AD_Process_ID reference="uuid" reference-key="AD_Process">{process-uuid}</AD_Process_ID>
  <FieldLength>10</FieldLength>   <!-- NOT NULL in DB — use 10 for Integer, 1 for List/YesNo -->
  ...
</AD_Process_Para>
```

**Reference → FieldLength defaults:**
- Integer (11): 10
- List (17): 1 (unless stored value can be longer)
- String (10): actual max length

### 11. iDempiere physical tables live in `adempiere` schema, not `public`

When checking physical table existence from psql or shell scripts:
```sql
-- WRONG: returns 0 rows
SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name ILIKE 'TW_%';

-- CORRECT
SELECT count(*) FROM pg_tables WHERE schemaname='adempiere' AND tablename ILIKE 'tw_%';
```

DROP commands in cleanup scripts must also use the schema prefix:
```sql
DROP TABLE IF EXISTS adempiere.TW_InvoicePrefix;
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
- **Never** pass `null` as trxName to `PackIn.importXML()` — throws `No Transaction Name` at runtime. Always create a named transaction: `String trxName = Trx.createTrxName("name"); Trx trx = Trx.get(trxName, true);`
- **Never** declare `Import-Package: org.adempiere.pipo2;version="[12.0.0,13.0.0)"` — pipo2 is exported without version (0.0.0), the version range causes bundle resolve failure. Use `org.adempiere.pipo2` with no version constraint
- **Never** override `PO.isActive()` — it is `final` in iDempiere 12.0 and will cause a compile error
- **Never** use `topic.endsWith("string")` to match event topics — use `IEventTopics.CONSTANT.equals(topic)`. String suffix matching silently fails at runtime when topic format changes
- **Never** allow status transition A→I for TW_InvoicePrefix — active prefixes cannot be deactivated (台灣稅法). Only forward transitions: I→A→C
- **Never** implement `ModelValidator` in `*Validator` classes — these are not registered as OSGi services; only static helper methods are needed
- **Never** skip `IProcessFactory` registration for `SvrProcess` subclasses — iDempiere's `DefaultProcessFactory` (in `org.adempiere.base`) cannot load classes from plugin bundles via `Class.forName()`. Every plugin that provides processes must implement `IProcessFactory` as an OSGi DS component (`OSGI-INF/*.xml`).
- **Never** use `(Integer) get_Value(col)` for List-reference columns — they are stored as CHAR/VARCHAR in the physical table, so `get_Value()` returns `String`. Use `instanceof` guard: `if (val instanceof String) return Integer.parseInt(...)`.
- **Never** pass int to `Query.setParameters()` for a List-reference column — the physical column is CHAR, so PostgreSQL rejects `character = integer`. Use `String.valueOf(intVal)`.
- **REST API process parameters format**: iDempiere REST API reads process params from **flat top-level JSON keys** (matching `columnName` or `propertyName`), NOT from a `parameters` array. Correct: `{"StatementYear": 2026, "StatementPeriod": "2"}`. Wrong: `{"parameters": [{"parameterName": "...", "value": ...}]}`.
- **List-type process parameters via REST API**: pass values as String (e.g. `"2"`), not integer, to ensure they reach `P_String` in `AD_PInstance_Para` correctly.
