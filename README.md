# iDempiere 台灣統一發票與營業稅系統

iDempiere 12.0 OSGi Plugin，實現符合台灣法規的統一發票（統一發票）與營業稅（401申報）管理。

**狀態**: 實作完成 ✅ | 61 tests 通過 ✅ | Review Gate 3-4 通過 ✅

---

## 快速開始

```bash
mvn compile      # 編譯
mvn test         # 執行測試（61 tests）
mvn package      # 打包 → target/tw.idempiere.invoice.tax-1.0.0-SNAPSHOT.jar
```

---

## 安裝

1. 將 `target/tw.idempiere.invoice.tax-1.0.0-SNAPSHOT.jar` 複製到 iDempiere 的 `plugins/` 目錄
2. 重啟 iDempiere
3. Bundle 啟動時會自動執行 `PackIn`，安裝 4 張 TW_* 資料表、視窗、選單

---

## 核心架構

```
Bundle 啟動
  └─ TaiwanInvoiceTaxActivator.start()
      └─ PackIn.importXML(tw_invoice_system.zip)
          └─ 安裝 AD_Table / AD_Window / AD_Menu 定義

資料模型（4張表）
  TW_InvoicePrefix      ← 字軌管理（AA, AB...）
  TW_Invoice_Prefix_Map ← 發票與字軌對應
  TW_InvoiceAdjustment  ← 銷項/進項折讓追蹤
  TW_TaxStatement       ← 401 申報表

服務層（純 Java，可單元測試）
  InvoiceNumberingService  ← 發票號碼分配（SELECT FOR UPDATE）
  TaxCalculationService    ← 稅額計算（BigDecimal + FLOOR）
  MixedBusinessService     ← 兼營比例調整
  InvoiceValidationService ← 驗證規則（警告不阻擋）

驗證器（ModelValidator，OSGi DS 服務）
  InvoicePrefixValidator    ← 字軌格式、號碼範圍、狀態轉換
  InvoicePrefixMapValidator ← 發票日期、買方統一編號格式

流程（SvrProcess）
  GenerateTaxStatementProcess ← 產生 401 申報表
  ExportTaxReportProcess      ← 匯出 MOF 電子申報 CSV
```

---

## 台灣法規核心規則

| 規則 | 說明 |
|------|------|
| 三聯式 (B2B) | 銷售額 × 1.05 = 含稅金額 |
| 二聯式 (B2C) | floor(含稅金額 ÷ 1.05) = 銷售額；稅額 = floor(銷售額 × 0.05) |
| 稅額計算 | 一律捨去法（FLOOR），不四捨五入（BigDecimal + RoundingMode.FLOOR） |
| 申報期間 | 雙月制：1-2月=第1期 … 11-12月=第6期 |
| 字軌狀態 | I（停用）→ A（使用中）→ C（已用完），C 不可逆，I 不可跳過 |
| 買方統一編號 | 三聯式（B2B）法定必填，須為 8 位純數字 |
| 進項折讓 | 必須當期申報；超期需使用者確認補稅風險 |
| 進項稅期限 | 10 年；90 天前警告 |
| 兼營調整 | 營業 < 9 個月免調整；比例 = 應稅銷售 / 總銷售（FLOOR） |
| 淨應納稅額 | 銷項稅 − (進項稅 − 不可扣抵進項稅) − 留抵稅額 |

---

## 實作進度

| Phase | 內容 | 狀態 |
|-------|------|------|
| 0.1 | 2Pack XML + ZIP | ✅ 完成 |
| 0.2 | Activator PackIn 邏輯 | ✅ 完成 |
| 0.3 | Model 類 + TaiwanModelFactory | ✅ 完成 |
| 2 | 服務層（TDD，29 tests） | ✅ 完成 |
| 3 | Validator（OSGi DS 服務，15 tests） | ✅ 完成 |
| 4 | Process（報表產生/匯出，8 tests） | ✅ 完成 |

---

## 文件

| 文件 | 說明 |
|------|------|
| `CLAUDE.md` | **必讀** — iDempiere 規則、domain 規則、禁止事項 |
| `docs/schema/table-definitions.md` | 4 張 TW_* 資料表欄位、型別、業務規則 |
| `docs/schema/packout-column-reference.md` | PackOut.xml 欄位參照（AD_Reference_ID 對照） |
| `docs/superpowers/plans/2026-03-25-tw-invoice-complete-plan.md` | 完整實作計劃（TDD, phase-by-phase） |
| `docs/design/Taiwan_Invoice_Tax_System_Final_Design_v3.md` | 最終設計文件，合規度 95/100 |
| `docs/design/Taiwan_Invoice_Tax_Design_Validation_Report.md` | 法規驗證報告 |

---

## 相關資源

- [財政部電子申報繳稅服務網](https://www.etax.nat.gov.tw/)
- [全國法規資料庫 — 加值型及非加值型營業稅法](https://law.moj.gov.tw/)
- [統一發票使用辦法](https://law.moj.gov.tw/)
- iDempiere 相關模組：`/home/tom/idempiere-tw-init-tenant/`
