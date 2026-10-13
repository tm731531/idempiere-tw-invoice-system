# E-Invoice Transmission Layer (Turnkey) — Design

> Date: 2026-10-13
> Status: Draft (brainstorming output, pending implementation plan)
> Scope: Add electronic-invoice **transmission** to the existing `tw.idempiere.invoice.tax` plugin.

All prose in English per repo policy; Taiwan regulatory terms kept in original (統一發票, C0401, MIG, …) where precision matters.

---

## 1. Why this exists

The existing plugin already manages **invoice numbering (字軌)**, **invoice records**, **allowances (折讓)**, and **401 business-tax filing (營業稅申報)**. It does **not** issue electronic invoices to the government. A repo-wide search confirms zero references to `C0401`, `C0501`, `MIG`, `Turnkey`, or `ECPay`.

This design adds the missing piece: turn an issued invoice into a **MIG XML message** and hand it to **Turnkey**, the Ministry of Finance (財政部) transmission agent.

### Decisions already made (do not re-litigate)

- **Placement = A**: this work lives **inside** `idempiere-tw-invoice-system`, not a separate plugin. (Decision 5347-01.)
- **Route = Turnkey-first, pluggable**: a `TransmissionAdapter` interface with a `TurnkeyFileAdapter` as the flagship; a VAN/ECPay adapter is a *later, optional* entry path, not in this design's scope.
- **First cut = C0401 (B2C 開立存證) only.** Void (C0501) and allowance messages are a second cut.

---

## 2. Key architectural fact: Turnkey is not an API

Turnkey is a **Ministry-of-Finance-provided program the merchant installs**. The integration contract is:

1. Our plugin **produces a MIG XML file (1 file = 1 invoice)**.
2. Our plugin **drops it into Turnkey's intake directory** (the B2C 存證 folder). High-volume sites can instead feed Turnkey's external RDB; **out of scope for the first cut — file-drop only.**
3. Turnkey signs it with the 工商憑證 and transmits over **SFTP** (`sftp.einvoice.nat.gov.tw`, test `tsftp.einvoice.nat.gov.tw`).

**Our plugin never touches SFTP, the certificate, or the government platform directly.** This makes the valuable core — MIG XML correctness — **unit-testable offline against the official C0401 sample/XSD**, with no cert and no government test environment required.

Current mandatory message spec: **MIG 4.x** (not 3.2.1 — that is obsolete). Exact minor version and XSD are pinned from the official MIG guide during implementation.

---

## 3. Component architecture

```
                 C_Invoice / C_InvoiceLine  (amounts, line items, buyer name)
                 AD_Org / AD_OrgInfo        (seller tax id, seller name)
TW_Invoice_Prefix_Map (invoice no., date, buyer tax id, prefix link)
        │
        ▼  read
┌───────────────────────────────────────────────────────────┐
│ einvoice/ (new sub-package, inside tw.idempiere.invoice.tax)│
│                                                             │
│  EInvoiceAssembler   pure Java: sources above → EInvoiceDoc │
│  EInvoiceDoc         canonical in-memory model (pure DTO)   │
│  MigC0401Builder     EInvoiceDoc → MIG 4.x C0401 XML        │
│  TransmissionAdapter interface { submit(xml, msg) }         │
│    └─ TurnkeyFileAdapter   write XML to Turnkey B2C folder  │
│  EInvoiceService     orchestrates assemble→build→submit     │
└───────────────────────────────────────────────────────────┘
        │ writes
        ▼
TW_EInvoiceMessage (new table) — one row per transmission attempt
```

- **EInvoiceAssembler / EInvoiceDoc / MigC0401Builder are pure Java** (no iDempiere runtime) → JUnit-testable, matching the existing `service/` layer convention.
- Trigger for the first cut is a **manual `SvrProcess`** ("Generate E-Invoice") run on a `TW_Invoice_Prefix_Map` record, registered through the existing `TaiwanProcessFactory` (`IProcessFactory`). An automatic event-driven trigger on invoice completion is deferred.

---

## 4. Data sourcing & gaps (the crux)

C0401 needs materially more data than `TW_Invoice_Prefix_Map` holds. Sources:

| C0401 data | Source | Status |
|---|---|---|
| 發票號碼 InvoiceNumber | `TW_Invoice_Prefix_Map.InvoiceNumber` | ✅ exists |
| 發票日期 InvoiceDate | `TW_Invoice_Prefix_Map.InvoiceDate` | ✅ exists |
| 買方統編 BuyerTaxID | `TW_Invoice_Prefix_Map.BuyerTaxID` | ✅ exists (B2C → `0000000000`) |
| 賣方統編 / 賣方名稱 | `AD_OrgInfo.TaxID` / Org name | ⚠️ needs Org config (verify `AD_OrgInfo.TaxID` populated) |
| 金額：總計 / 銷售額 / 稅額 | `C_Invoice` (via `C_Invoice_ID` FK) | ⚠️ from linked invoice; confirm tax-inclusive handling + FLOOR rule reuse |
| 品項明細 ProductItem (品名/數量/單價/金額) | `C_InvoiceLine` | ⚠️ from linked invoice lines |
| 課稅別 TaxType (1應稅/2零稅/3免稅) | derive from `C_Tax` / `InvoiceType`; first cut fixes **1 應稅** | ⚠️ derivation deferred; skeleton = 應稅 |
| **隨機碼 RandomNumber (4 digits)** | — | ❌ **missing — must generate & persist** |
| **載具類別/號碼 Carrier** | — | ❌ **missing (B2C 載具 out of first-cut scope)** |
| **捐贈/愛心碼 Donation (NPOBAN)** | — | ❌ **missing (donation out of first-cut scope)** |

Correction to earlier notes: `TW_Invoice_Prefix_Map` has **no carrier column** — it holds only `BuyerTaxID`. 載具 appears only in validator logic, not as stored data.

---

## 5. New table: `TW_EInvoiceMessage`

One row per transmission attempt (installed via a 2Pack version bump; follows all existing 2Pack/AD_Field rules in `CLAUDE.md`).

| Column | Type | Note |
|---|---|---|
| TW_EInvoiceMessage_ID / _UU | PK / UUID | standard |
| AD_Client_ID / AD_Org_ID / IsActive / audit | | standard set |
| TW_Invoice_Prefix_Map_ID | TableDir FK | the invoice being transmitted |
| MessageType | List | first cut: `C0401` only |
| RandomNumber | String(4) | the 4-digit 隨機碼, generated once, persisted |
| MigVersion | String | e.g. `4.0` |
| Status | List | `DRAFT` → `WRITTEN` (file dropped) → `CONFIRMED` / `FAILED` |
| XmlFileName | String | the file written to Turnkey intake |
| ErrorMessage | String | on failure |
| GeneratedOn | DateTime | |

`RandomNumber` lives here (not on the map) because it is a transmission artifact, and a re-issue/retry must reuse the same invoice-number+random-code pair.

---

## 6. First-cut scope (walking skeleton)

**Goal:** prove one B2C taxable invoice becomes a valid C0401 XML dropped for Turnkey.

In scope:
1. `TW_EInvoiceMessage` table + `MEInvoiceMessage` model (`@Model`, `TaiwanModelFactory` already scans the `model` package).
2. `EInvoiceAssembler`: read one `TW_Invoice_Prefix_Map` + its `C_Invoice`/`C_InvoiceLine` + Org → `EInvoiceDoc`. B2C, 應稅, no carrier, no donation.
3. `MigC0401Builder`: `EInvoiceDoc` → MIG 4.x C0401 XML.
4. `TurnkeyFileAdapter`: write XML to a configurable intake directory (path from `AD_SysConfig`, default a local folder).
5. `GenerateEInvoiceProcess` (`SvrProcess`) wired via existing `TaiwanProcessFactory`.
6. `RandomNumber` generation (persisted, reused on retry).

Out of scope (later cuts): C0501 void, allowance messages, B2C carrier (手機條碼/自然人憑證), donation (愛心碼), B2B 交換 mode, Turnkey RDB feed, auto-trigger on invoice completion, VAN/ECPay adapter, real SFTP/cert anything.

---

## 7. Error handling

- Assembler validation failures (missing seller tax id, no invoice lines, amount mismatch) → throw `AdempiereException` with a specific message; do **not** write a file; record `Status=FAILED` + `ErrorMessage`.
- Monetary math reuses the existing `TaxCalculationService` FLOOR rules; **never** `float`/`double`, **never** `ROUND`.
- File-drop failure (dir missing/unwritable) → `FAILED`, surfaced to the user running the process.
- Idempotency: a map record already `WRITTEN`/`CONFIRMED` is not re-dropped unless explicitly retried.

---

## 8. Testing

- **Pure-Java unit tests** (JUnit, no iDempiere runtime) for `EInvoiceAssembler` and `MigC0401Builder`.
- **Golden-file test:** generated C0401 XML validated against the **official Ministry-of-Finance C0401 sample + XSD** (committed under `test/resources/mig/`). This is the acceptance gate — correctness is proven without any cert or government environment.
- Random-number field asserted 4 digits and stable across retry.
- Follows the existing 89-test suite's style; target: all existing tests stay green.

---

## 9. Open items to pin during implementation

1. Exact MIG minor version + official C0401 XSD/sample URL (from einvoice.nat.gov.tw).
2. Confirm `AD_OrgInfo.TaxID` is the right seller-tax-id source (or add Org-level config).
3. Confirm amount/line mapping from `C_Invoice`/`C_InvoiceLine` (tax-inclusive vs exclusive, rounding boundary).
4. Turnkey intake directory layout (B2C 存證 subfolder naming) from the Turnkey manual.
