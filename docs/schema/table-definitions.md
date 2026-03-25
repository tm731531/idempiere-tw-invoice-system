# Taiwan Invoice System — Table Definitions

> **重要：這是文件說明，不是部署腳本。**
> 實體資料表由 2Pack（`resources/2pack/tw_invoice_system.zip`）安裝時自動建立。
> PackOut.xml 是唯一的 schema 定義來源。

---

## TW_InvoicePrefix — 發票字軌管理

| Column | Type | Mandatory | Description |
|--------|------|-----------|-------------|
| TW_InvoicePrefix_ID | Integer (PK) | Y | Primary key |
| TW_InvoicePrefix_UU | String(36) | N | UUID for replication |
| AD_Client_ID | TableDir | Y | Client |
| AD_Org_ID | TableDir | Y | Organization |
| IsActive | YesNo | Y | Active flag |
| Created | DateTime | Y | Created timestamp |
| CreatedBy | TableDir | Y | Created by user |
| Updated | DateTime | Y | Updated timestamp |
| UpdatedBy | TableDir | Y | Updated by user |
| PrefixCode | String(2) | Y | 2-char uppercase code (AA–ZZ)，財政部核配 |
| InvoiceType | List | Y | `SALES_TRIPART`（三聯式）/ `SALES_BIPART`（二聯式） |
| PrefixStartDate | Date | Y | 字軌有效起始日（財政部核配，2個月一期） |
| PrefixEndDate | Date | Y | 字軌有效截止日 |
| StartNumber | Integer | Y | 起始號碼（通常為 1） |
| EndNumber | Integer | Y | 結束號碼 |
| CurrentNumber | Integer | Y | 目前已用到的號碼 |
| LastInvoiceNumber | String(10) | N | 最後開立的發票號碼（格式：AA00000001） |
| LastIssuedInvoiceDate | Date | N | 最後開立日期 |
| Status | List | Y | `A`（Active）/ `I`（Inactive）/ `C`（Complete，不可逆） |

**業務規則：**
- PrefixCode 必須為 2 個大寫英文字母
- StartNumber < EndNumber
- Status 流轉：I → A → C，C 不可回到 A
- 開立發票時驗證：`DateInvoiced BETWEEN PrefixStartDate AND PrefixEndDate`
- CurrentNumber > EndNumber 時自動設 Status = C

---

## TW_Invoice_Prefix_Map — 發票與字軌對應

| Column | Type | Mandatory | Description |
|--------|------|-----------|-------------|
| TW_Invoice_Prefix_Map_ID | Integer (PK) | Y | Primary key |
| TW_Invoice_Prefix_Map_UU | String(36) | N | UUID |
| AD_Client_ID | TableDir | Y | Client |
| AD_Org_ID | TableDir | Y | Organization |
| IsActive | YesNo | Y | Active flag |
| Created | DateTime | Y | - |
| CreatedBy | TableDir | Y | - |
| Updated | DateTime | Y | - |
| UpdatedBy | TableDir | Y | - |
| TW_InvoicePrefix_ID | TableDir (FK) | Y | FK → TW_InvoicePrefix |
| C_Invoice_ID | TableDir (FK) | Y | FK → C_Invoice |
| InvoiceDate | Date | Y | 發票開立日期（不可為未來日期） |
| InvoiceNumber | String(10) | Y | 完整發票號碼（如 AA00000001） |
| BuyerTaxID | String(8) | N | 買方統一編號（三聯式法定必填，二聯式可空） |
| IsExpiryWarning | YesNo | Y | 進項稅 10 年到期警告旗標 |

**業務規則：**
- `InvoiceDate <= TODAY()`（不可未來日期，hard block）
- `InvoiceType = SALES_TRIPART` 時，`BuyerTaxID IS NOT NULL`
- 同字軌 InvoiceDate 應遞增（警告，不阻擋）
- InvoiceDate 月份應與 C_Invoice 交易月份一致（警告，不阻擋）

---

## TW_InvoiceAdjustment — 發票折讓追蹤

| Column | Type | Mandatory | Description |
|--------|------|-----------|-------------|
| TW_InvoiceAdjustment_ID | Integer (PK) | Y | Primary key |
| TW_InvoiceAdjustment_UU | String(36) | N | UUID |
| AD_Client_ID | TableDir | Y | Client |
| AD_Org_ID | TableDir | Y | Organization |
| IsActive | YesNo | Y | Active flag |
| Created | DateTime | Y | - |
| CreatedBy | TableDir | Y | - |
| Updated | DateTime | Y | - |
| UpdatedBy | TableDir | Y | - |
| C_Invoice_ID | TableDir (FK) | N | FK → C_Invoice（被折讓的原始發票） |
| AdjustmentType | List | Y | `RETURN`（退貨）/ `ALLOWANCE`（折讓） |
| AdjustmentDirection | List | Y | `SALES`（銷項折讓，我方開折讓單給買方）/ `PURCHASE`（進項折讓，供應商開折讓單給我方） |
| AdjustmentDate | Date | Y | 折讓發生日期 |
| AdjustedTaxAmount | Decimal(15,2) | Y | 折讓稅額（正值，方向由 AdjustmentDirection 決定） |
| TaxPeriod | List | Y | 應申報的雙月期（1–6） |
| IsOverduePeriod | YesNo | Y | 是否超過當期申報（超期需使用者確認稅務風險） |

**業務規則：**
- 折讓必須在發生當期（雙月期）申報
- 超期申報有補稅/裁罰風險，需使用者主動確認，非單純警告
- SALES 方向影響銷項稅；PURCHASE 方向影響進項稅

---

## TW_TaxStatement — 401 申報表

| Column | Type | Mandatory | Description |
|--------|------|-----------|-------------|
| TW_TaxStatement_ID | Integer (PK) | Y | Primary key |
| TW_TaxStatement_UU | String(36) | N | UUID |
| AD_Client_ID | TableDir | Y | Client |
| AD_Org_ID | TableDir | Y | Organization |
| IsActive | YesNo | Y | Active flag |
| Created | DateTime | Y | - |
| CreatedBy | TableDir | Y | - |
| Updated | DateTime | Y | - |
| UpdatedBy | TableDir | Y | - |
| StatementYear | Integer | Y | 申報年度（西元） |
| StatementPeriod | List | Y | 申報期別（1–6，雙月） |
| TaxableRevenue | Decimal(15,2) | Y | 應稅銷售額 |
| ZeroRateSalesAmount | Decimal(15,2) | Y | 零稅率銷售額（出口，可申請退稅） |
| ExemptRevenue | Decimal(15,2) | Y | 免稅銷售額 |
| OutputTaxAmount | Decimal(15,2) | Y | 銷項稅額 |
| InputTaxAmount | Decimal(15,2) | Y | 進項稅額合計 |
| NonDeductibleInputTax | Decimal(15,2) | Y | 不可扣抵進項稅額 |
| CarryOverTaxCredit | Decimal(15,2) | Y | 上期累積留抵稅額 |
| TaxableRatio | Decimal(10,4) | N | 兼營應稅比例（兼營時才有值） |
| AdjustedInputTax | Decimal(15,2) | N | 調整後可扣抵進項稅額（兼營計算結果） |
| TaxPayable | Decimal(15,2) | Y | 本期應納/溢付稅額（負值=溢付可留抵或退稅） |
| IsMixedBusiness | YesNo | Y | 是否為兼營營業人 |
| FilingDueDate | Date | N | 申報截止日（次月 15 日，遇假日順延） |

**業務規則：**
- 申報期間：1=(1-2月), 2=(3-4月), 3=(5-6月), 4=(7-8月), 5=(9-10月), 6=(11-12月)
- 零稅率（出口）≠ 免稅：需獨立填列，可申請退稅
- 兼營 < 9 個月免調整；比例 = 應稅銷售 / 總銷售
- TaxPayable = OutputTax - AdjustedInputTax - CarryOverTaxCredit
