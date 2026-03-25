# Taiwan Invoice Tax System — Agent Team

> This file defines the agent team for implementing the Taiwan Invoice Tax System iDempiere plugin.
> Each agent has a specific role, model, and scope of responsibility.
> Execution follows the plan at `docs/superpowers/plans/2026-03-25-tw-invoice-complete-plan.md`.

## Team Overview

| Agent | Model | Role | Phase |
|-------|-------|------|-------|
| `pack-builder` | sonnet | Build 2Pack XML + ZIP | 0.1 |
| `activator-fixer` | sonnet | Activator PackIn logic + MANIFEST | 0.2 |
| `model-fixer` | sonnet | Fix 4 Models + TaiwanModelFactory + component.xml | 0.3 |
| `service-builder` | sonnet | Phase 2 pure-Java service layer (TDD) | 2 |
| `integration-builder` | sonnet | Phase 3 validators/callouts, Phase 4 processes | 3–4 |
| `idempiere-expert` | opus | Technical reviewer: OSGi, 2Pack format, Model correctness | All |
| `tw-invoice-pm` | sonnet | Domain reviewer: Taiwan invoice law compliance, UI completeness | All |

---

## Agent Responsibilities

### `pack-builder`

**Goal:** Produce a valid `resources/2pack/tw_invoice_system.zip` containing `tw_invoice_system/dict/PackOut.xml`.

**Owns:**
- `resources/2pack/tw_invoice_system/dict/PackOut.xml`
- `resources/2pack/tw_invoice_system.zip`
- Deletion of old `2pack/*.xml` files

**Must validate before handing off:**
- `xmllint --noout PackOut.xml` exits 0
- `unzip -l tw_invoice_system.zip` shows `tw_invoice_system/dict/PackOut.xml`
- All ColumnName values match Java `COLUMNNAME_*` constants in Model classes

**Hands off to:** `idempiere-expert` + `tw-invoice-pm` for review, then `activator-fixer`

---

### `activator-fixer`

**Goal:** Update `TaiwanInvoiceTaxActivator.java` to call `PackIn.importXML()` on bundle start, and update `MANIFEST.MF` to import `org.adempiere.pipo2`.

**Owns:**
- `src/tw/idempiere/invoice/tax/TaiwanInvoiceTaxActivator.java`
- `META-INF/MANIFEST.MF`

**Must validate before handing off:**
- `mvn compile` exits 0
- Test `ActivatorPackInTest.testPackInResourceExists()` passes

**Depends on:** `pack-builder` completing 0.1 (ZIP must exist at correct path)

**Hands off to:** `idempiere-expert` for OSGi review

---

### `model-fixer`

**Goal:** Fix 3 production crash bugs in 4 Model classes, and create `TaiwanModelFactory` as OSGi service.

**Owns:**
- `src/tw/idempiere/invoice/tax/model/TaiwanModelFactory.java` (new)
- `src/tw/idempiere/invoice/tax/model/MInvoicePrefix.java`
- `src/tw/idempiere/invoice/tax/model/MInvoicePrefixMap.java`
- `src/tw/idempiere/invoice/tax/model/MInvoiceAdjustment.java`
- `src/tw/idempiere/invoice/tax/model/MTaxStatement.java`
- `OSGI-INF/component.xml`

**Must fix (per model class):**
1. Add `@Model(table = Table_Name)` annotation
2. Fix `initPO()` to use `POInfo.getPOInfo(ctx, Table_Name)` — never return null
3. Confirm `get_AccessLevel()` returns `3`

**Must validate before handing off:**
- `mvn compile` exits 0
- `testModelAnnotationPresent()` tests pass for all 4 models

**Hands off to:** `idempiere-expert` for OSGi + Model review

---

### `service-builder`

**Goal:** Implement Phase 2 pure-Java service classes using TDD.

**Owns:**
- `src/tw/idempiere/invoice/tax/service/InvoiceNumberingService.java`
- `src/tw/idempiere/invoice/tax/service/TaxCalculationService.java`
- `src/tw/idempiere/invoice/tax/service/MixedBusinessService.java`
- `src/tw/idempiere/invoice/tax/service/InvoiceValidationService.java`
- `test/tw/idempiere/invoice/tax/service/*Test.java`

**TDD discipline required:** Write failing test → verify fail → implement → verify pass → commit. No skipping steps.

**Must validate before handing off:**
- `mvn test -Dtest=*ServiceTest` — all tests pass
- Tax calculation uses `BigDecimal + RoundingMode.FLOOR` (no `Math.round`)
- No iDempiere runtime dependency in service layer

**Hands off to:** `tw-invoice-pm` for domain review

---

### `integration-builder`

**Goal:** Implement Phase 3 validators/callouts and Phase 4 process classes.

**Owns:**
- `src/tw/idempiere/invoice/tax/callout/InvoicePrefixCallout.java`
- `src/tw/idempiere/invoice/tax/callout/TaxStatementCallout.java`
- `src/tw/idempiere/invoice/tax/process/GenerateTaxStatementProcess.java`
- `src/tw/idempiere/invoice/tax/process/ExportTaxReportProcess.java`
- Registration entries in `OSGI-INF/component.xml`

**Must validate before handing off:**
- `mvn compile` exits 0
- `@ModelEvent` / `@Callout` / `@Process` annotations present and correct
- Each validator in Phase 3 has at least 2 unit tests

**Hands off to:** `idempiere-expert` + `tw-invoice-pm` for final review

---

### `idempiere-expert`

**Role:** Technical gatekeeper. Reviews all iDempiere-specific correctness.

**Review checklist (apply to every handoff):**

**OSGi / Bundle:**
- [ ] `MANIFEST.MF` imports: `org.adempiere.base`, `org.adempiere.pipo2`, `org.compiere.model`, `org.compiere.util`, `org.osgi.framework`
- [ ] `Bundle-ClassPath` includes resources path if 2Pack ZIP is embedded
- [ ] `Service-Component` wildcard covers all new component XML files
- [ ] No circular imports between packages

**2Pack / PackOut.xml:**
- [ ] Root element has all required attributes (Name, Version, idempiereVersion, etc.)
- [ ] AD_EntityType defined before AD_Table
- [ ] AD_Reference defined before AD_Table/AD_Column that reference them
- [ ] Every PK column: `<AD_Reference_ID>13</AD_Reference_ID>` (ID type)
- [ ] Every FK column: `<AD_Reference_ID>19</AD_Reference_ID>` (TableDir) or `30` (Search)
- [ ] Every record has stable UUID fields (`AD_Table_UU`, `AD_Column_UU`, etc.)
- [ ] `ColumnName` in XML exactly matches `COLUMNNAME_*` Java constant
- [ ] Standard columns present: `AD_Client_ID`, `AD_Org_ID`, `IsActive`, `Created`, `CreatedBy`, `Updated`, `UpdatedBy`
- [ ] `AD_Window` → `AD_Tab` → `AD_Field` hierarchy correct
- [ ] `AD_Menu` entry exists for each `AD_Window`

**Model classes:**
- [ ] `@Model(table = Table_Name)` annotation present
- [ ] `initPO()` never returns null — uses `POInfo.getPOInfo(ctx, Table_Name)`
- [ ] `get_AccessLevel()` returns same value as `<AccessLevel>` in 2Pack
- [ ] All 3 constructors: `(ctx, int, trxName)`, `(ctx, String uuid, trxName)`, `(ctx, ResultSet, trxName)`
- [ ] `Table_ID` is `0` (not hardcoded non-zero)

**TaiwanModelFactory:**
- [ ] Extends `AnnotationBasedModelFactory`
- [ ] `@Component(service = IModelFactory.class)` annotation present
- [ ] `getPackages()` returns correct package path
- [ ] Registered in `OSGI-INF/component.xml`

**Activator:**
- [ ] `PackIn.importXML()` called (not `PackInProcess`)
- [ ] ZIP loaded via `BundleContext.getBundle().getResource()`
- [ ] Failure is non-fatal (warn + continue, tables may already exist)

---

### `tw-invoice-pm`

**Role:** Domain gatekeeper. Reviews Taiwan invoice law compliance and feature completeness.

**Review checklist (apply to every handoff):**

**Data model completeness:**
- [ ] `TW_InvoicePrefix`: PrefixCode (2 uppercase letters), InvoiceType, StartNumber, EndNumber, CurrentNumber, Status (A/I/C), LastIssuedInvoiceDate
- [ ] `TW_Invoice_Prefix_Map`: FK to TW_InvoicePrefix, FK to C_Invoice, InvoiceDate, InvoiceNumber
- [ ] `TW_InvoiceAdjustment`: AdjustmentType (RETURN/ALLOWANCE), AdjustmentDate, TaxPeriod, AdjustedTaxAmount, FK to C_Invoice
- [ ] `TW_TaxStatement`: TaxYear, TaxPeriod (1–6), OutputTax, InputTax, TaxableRatio, TaxPayable, IsMixedBusiness

**Business rule compliance:**
- [ ] 三聯式: `SaleAmount × 1.05 = GrossAmount` (buyer has tax ID)
- [ ] 二聯式: `floor(GrossAmount / 1.05) = SaleAmount` (no tax ID)
- [ ] Tax amount always computed with `FLOOR`, not `ROUND`
- [ ] Bimonthly periods: 1=(Jan-Feb), 2=(Mar-Apr), 3=(May-Jun), 4=(Jul-Aug), 5=(Sep-Oct), 6=(Nov-Dec)
- [ ] Status lifecycle: I → A → C, C cannot revert to A
- [ ] Future invoice date → hard block (error)
- [ ] Non-sequential dates within same prefix → warning only (not block)
- [ ] Invoice month ≠ transaction month → warning only (not block)
- [ ] Adjustment not in current period → warning only (not block, per current design)
- [ ] 兼營: < 9 months operating → no adjustment; ratio = taxable / total
- [ ] Input tax expiry 10 years; warn 90 days before

**UI completeness:**
- [ ] All 4 tables have AD_Window with correct tabs and fields visible
- [ ] All windows accessible via AD_Menu (otherwise users can't navigate to them)
- [ ] Field labels in Traditional Chinese where applicable

**Export completeness (Phase 4):**
- [ ] Export format matches Ministry of Finance specification
- [ ] Period selection (year + period) correctly filters records

---

## Execution Protocol

### Phase Gate Rules

1. **No phase starts until previous phase's review gates pass**
2. `idempiere-expert` and `tw-invoice-pm` review independently — both must approve
3. If either reviewer raises a blocker: implementing agent fixes before proceeding
4. Maximum 3 review iterations per phase — escalate to human if not resolved

### Execution Order

```
Phase 0.1 (pack-builder)
  └─ Review Gate 0.1: idempiere-expert + tw-invoice-pm
      └─ Phase 0.2 (activator-fixer)    Phase 0.3 (model-fixer) [parallel]
          └─ Review Gate 0.2/0.3: idempiere-expert
              └─ Phase 1 check: idempiere-expert (column name alignment)
                  └─ Phase 2 (service-builder)
                      └─ Review Gate 2: tw-invoice-pm
                          └─ Phase 3 (integration-builder)
                              └─ Review Gate 3: idempiere-expert + tw-invoice-pm
                                  └─ Phase 4 (integration-builder)
                                      └─ Review Gate 4: idempiere-expert + tw-invoice-pm
```

### Communication Protocol

- Each implementing agent writes a **handoff note** at end of task: what was done, what files changed, any unresolved questions
- Reviewers respond with: ✅ Approved / ❌ Blocked (with specific issue + fix required)
- All review comments reference specific file + line number where possible
