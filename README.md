# iDempiere 台灣統一發票與營業稅系統

iDempiere 12.0 OSGi Plugin，實現符合台灣法規的統一發票（統一發票）與營業稅（401申報）管理。

**狀態**: 設計完成 ✅ | Plugin 骨架就緒 ✅ | 實作進行中 🚧

---

## 快速開始

```bash
mvn compile      # 編譯
mvn test         # 執行測試
mvn package      # 打包
```

---

## 專案文件

### 設計文件
| 文件 | 內容 |
|------|------|
| `Taiwan_Invoice_Tax_System_Final_Design_v3.md` | **開發直接依據** — 最終設計，合規度 95/100 |
| `Taiwan_Invoice_Tax_System_Revised_Design_v2.md` | 設計基礎 — 含 6 項重大缺陷修正過程 |
| `Taiwan_Invoice_Tax_Design_Validation_Report.md` | 法規驗證報告（網路查證 + 法規引用） |
| `Taiwan_Invoice_System_Expert_Review_Checklist.md` | 財會稅務專家審查清單 |

### 開發文件
| 文件 | 內容 |
|------|------|
| `CLAUDE.md` | **必讀** — iDempiere 規則、domain 規則、禁止事項 |
| `AGENTS.md` | Agent Team 定義 — 角色職責、審查 checklist、執行順序 |
| `docs/superpowers/plans/2026-03-25-tw-invoice-complete-plan.md` | 完整實作計劃（TDD, phase-by-phase） |

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
  TW_InvoiceAdjustment  ← 進項折讓追蹤
  TW_TaxStatement       ← 401 申報表

服務層（純 Java，可單元測試）
  InvoiceNumberingService  ← 發票號碼分配（SELECT FOR UPDATE）
  TaxCalculationService    ← 稅額計算（BigDecimal + FLOOR）
  MixedBusinessService     ← 兼營比例調整
  InvoiceValidationService ← 驗證規則（警告不阻擋）
```

---

## 台灣法規核心規則

| 規則 | 說明 |
|------|------|
| 三聯式 (B2B) | 銷售額 × 1.05 = 含稅金額 |
| 二聯式 (B2C) | floor(含稅金額 ÷ 1.05) = 銷售額 |
| 稅額計算 | 一律捨去法（FLOOR），不四捨五入 |
| 申報期間 | 雙月制：1-2月=第1期 … 11-12月=第6期 |
| 字軌狀態 | I（停用）→ A（使用中）→ C（已用完），C 不可逆 |
| 進項折讓 | 必須當期申報 |
| 進項稅期限 | 10 年；90 天前警告 |
| 兼營調整 | 營業 < 9 個月免調整；比例 = 應稅銷售 / 總銷售 |

---

## 實作進度

| Phase | 內容 | 狀態 |
|-------|------|------|
| 0.1 | 2Pack XML + ZIP | ⬜ 待開始 |
| 0.2 | Activator PackIn 邏輯 | ⬜ 待開始 |
| 0.3 | 修正 Model 類 + TaiwanModelFactory | ⬜ 待開始 |
| 2 | 服務層（TDD） | ⬜ 待開始 |
| 3 | Validator + Callout | ⬜ 待開始 |
| 4 | Process（報表產生/匯出） | ⬜ 待開始 |

---

## 相關資源

- [財政部電子申報繳稅服務網](https://www.etax.nat.gov.tw/)
- [全國法規資料庫](https://law.moj.gov.tw/)
- iDempiere 相關模組：`/home/tom/idempiere-tw-init-tenant/`
