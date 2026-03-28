# Phase 1 Summary — Taiwan Invoice Tax System for iDempiere

## Overview

Phase 1 delivers the foundational data model, validation layer, and accounting-code
mapping service for the Taiwan Unified Invoice (統一發票) and Business Tax system
implemented as an iDempiere OSGi bundle.

---

## 1. Database Tables Created

| Table | Description | SQL File |
|-------|-------------|----------|
| `TW_InvoicePrefix` | Invoice prefix (字軌) blocks assigned by MOF | `01_create_tw_invoice_prefix.sql` |
| `TW_Invoice_Prefix_Map` | Maps each C_Invoice to its prefix + validation flags | `02_create_tw_invoice_prefix_map.sql` |
| `TW_InvoiceAdjustment` | Debit-note / return adjustments (進項折讓) | `03_create_tw_invoice_adjustment.sql` |
| `TW_TaxStatement` | Bimonthly VAT return (401 form) | `04_create_tw_tax_statement.sql` |
| *(accounting codes)* | SQL reference for chart-of-accounts mapping | `05_accounting_codes_mapping.sql` |

> **Note:** All tables have `Table_ID = 0` in the current code. Each table must be
> registered in `AD_Table` via the iDempiere System Dictionary before production
> deployment. The `initPO()` method logs a warning and returns `null` until then;
> the model still compiles and validators work correctly.

---

## 2. Model Classes

| Class | Package | Description |
|-------|---------|-------------|
| `MInvoicePrefix` | `tw.idempiere.invoice.tax.model` | Invoice prefix (字軌) block — status lifecycle: Inactive → Active → Complete |
| `MInvoicePrefixMap` | `tw.idempiere.invoice.tax.model` | Links C_Invoice to prefix; tracks date-sequence, month-consistency, and 10-year expiry |
| `MInvoiceAdjustment` | `tw.idempiere.invoice.tax.model` | Input-tax adjustment (退回/折讓) with bimonthly reporting period |
| `MTaxStatement` | `tw.idempiere.invoice.tax.model` | VAT return (401 form) for ordinary and mixed-business (兼營) operators |

All Model classes extend `org.compiere.model.PO` and delegate all field validation
to their corresponding pure-Java Validator class.

---

## 3. Validation Rules

### InvoicePrefixValidator
- `PrefixCode` must be exactly 2 uppercase ASCII letters (e.g., `AA`–`ZZ`)
- `StartNumber` ≥ 1
- `EndNumber` ≥ `StartNumber`
- For new records with `CurrentNumber == 0`, auto-initialise to `StartNumber`

### InvoicePrefixMapValidator
- `TW_InvoicePrefix_ID` > 0
- `C_Invoice_ID` > 0
- `InvoiceNumber` matches `[A-Z]{2}\d{7,8}`
- `DateInvoiced` not null
- Compute `IsExpiryWarning = Y` when invoice is within 90 days of the 10-year input-tax claim deadline

### InvoiceAdjustmentValidator
- `TW_InvoicePrefixMap_ID` > 0
- `AdjustmentType` one of `RETURN` | `ALLOWANCE`
- `AdjustmentAmount` > 0
- `InputTaxAmount` ≥ 0 and ≤ `AdjustmentAmount`
- `RequiredReportingPeriod` format `YYYYMM`, month must be `01/03/05/07/09/11`

### TaxStatementValidator
- `StatementPeriod` 1–6 (bimonthly)
- `StatementYear` ≥ 2000
- `TaxableRevenue`, `ExemptRevenue`, `OutputTaxAmount`, `InputTaxAmount` all ≥ 0
- Mixed-business (`IsMixedBusiness = Y`): `MixedBusinessRatio` must be in [0, 1]
- Ordinary business: `MixedBusinessRatio` must be null
- Computed: `TaxPayable = OutputTax − (InputTax × ratio)` where ratio = 1.0 for ordinary

---

## 4. Accounting Code Mapping

The `AccountingCodeMapper` service resolves Taiwan chart-of-accounts codes
dynamically at runtime by querying `C_ElementValue`, keyed on `AD_Client_ID`.

| Account Code | Chinese Name | Account Type | Description |
|-------------|-------------|-------------|-------------|
| `1112` | 應收進項稅額 | Asset (A) | Input Tax Receivable |
| `2121` | 應付營業稅 | Liability (L) | Output Tax Payable |
| `4100` | 銷售收入-應稅 | Revenue (R) | Taxable Sales Revenue |
| `4200` | 銷售收入-免稅 | Revenue (R) | Tax-Exempt Sales Revenue |
| `5100` | 銷貨成本 | Expense (E) | Cost of Goods Sold |

Design decisions:
- **No hard-coded IDs**: accounts are resolved by `Value` string, portable across installations
- **Tenant isolation**: each `AD_Client_ID` has a separate cache entry
- **Graceful degradation**: missing accounts return `null` + warning log (no exception)
- **Thread safety**: `ConcurrentHashMap` with immutable snapshot inner maps
- **Cache invalidation**: `invalidateCache(clientId)` or `invalidateAllCaches()`

The `AccountingCodeMapperValidator` validates all five mandatory accounts exist and
have the correct account type; mismatched types produce warnings rather than errors.

---

## 5. Test Coverage Statistics

| Scope | Tests | Coverage |
|-------|-------|----------|
| All tests (total) | 110 | — |
| Phase 1 integration tests | 11 | — |
| Existing unit tests | 99 | — |
| **Testable classes (excluding PO/OSGi runtime classes)** | — | **88.5%** |
| Pure-Java validators | — | 95–100% |
| AccountingCodeMapper + Validator | — | 83–100% |

**Coverage tool:** JaCoCo 0.8.11, configured in `pom.xml` with:
- Overall testable-bundle threshold: ≥ 80% instruction coverage
- Per-class threshold for validators: ≥ 90% instruction coverage
- Report: `target/site/jacoco/index.html`

### Why the 4 Model classes have 0% JaCoCo coverage
`MInvoicePrefix`, `MInvoicePrefixMap`, `MInvoiceAdjustment`, and `MTaxStatement`
extend iDempiere's `org.compiere.model.PO` class. The PO constructor accesses the
iDempiere cache and Caffeine library at class-load time, which requires a running
iDempiere server and database. These classes are excluded from the JaCoCo coverage
threshold check in `pom.xml` (the `<excludes>` block). Their business logic is fully
tested via the corresponding pure-Java Validator classes (which have no iDempiere
dependency and achieve 95–100% coverage).

---

## 6. Bundle Structure

```
META-INF/MANIFEST.MF           OSGi bundle descriptor
OSGI-INF/component.xml         DS component definition
src/tw/idempiere/invoice/tax/
  TaiwanInvoiceTaxActivator    OSGi BundleActivator
  model/
    MInvoicePrefix             Model class + constants
    MInvoicePrefixMap          Model class + constants
    MInvoiceAdjustment         Model class + constants
    MTaxStatement              Model class + constants
    InvoicePrefixValidator     Pure-Java validator (no PO dep)
    InvoicePrefixMapValidator  Pure-Java validator (no PO dep)
    InvoiceAdjustmentValidator Pure-Java validator (no PO dep)
    TaxStatementValidator      Pure-Java validator (no PO dep)
  service/
    AccountingCodeMapper       Dynamic chart-of-accounts query + cache
    AccountingCodeMapperValidator  Validates mandatory accounts
  util/
    AccountingCodes            Default account code constants
```

**Artifact:** `target/tw.idempiere.invoice.tax-1.0.0-SNAPSHOT.jar`
**OSGi Symbolic Name:** `tw.idempiere.invoice.tax;singleton:=true`
**Required iDempiere:** `org.adempiere.base;bundle-version="12.0.0"`

---

## 7. Known Limitations / Pending Items

1. **Table_ID = 0 for all tables** — All four tables must be registered in the
   iDempiere System Dictionary (`AD_Table`, `AD_Column`) before any PO save operations
   will work in a real server. Set `Table_ID` in each Model class after registration.

2. **No database migrations** — No Liquibase/Flyway changeset has been created.
   The SQL scripts in `src/main/resources/sql/` must be run manually during installation.

3. **TaiwanInvoiceTaxActivator has 0% coverage** — The OSGi BundleActivator requires
   a running OSGi container (Equinox). Integration tests for it are outside the scope
   of a plain JUnit build.

4. **AccountingCodeMapper.loadAccountsFromDB not covered** — This method calls
   `org.compiere.util.DB.prepareStatement`, which requires a live database. It is
   tested via the `StubAccountingCodeMapper` subclass pattern.

5. **Mixed-business ratio is not auto-computed** — The current implementation requires
   the caller to supply `MixedBusinessRatio`. A future enhancement could auto-compute
   it from the prior year's revenue split.

6. **Phase 2 not started** — Invoice issuance workflow, reporting to MoF's e-GIF API,
   and the Taiwan e-Invoice (電子發票) integration are planned for Phase 2.
