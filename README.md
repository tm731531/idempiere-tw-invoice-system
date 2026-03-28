# iDempiere 台灣統一發票與營業稅系統

iDempiere 12.0 OSGi Plugin，實現符合台灣法規的統一發票（統一發票）與營業稅（401申報）管理。

**狀態**: 實作完成 ✅ | 89 tests 通過 ✅ | 部署驗證通過 ✅ | 2Pack 1.0.11

---

## 快速開始

```bash
mvn compile      # 編譯
mvn test         # 執行測試（89 tests）
mvn package      # 打包 → target/tw.idempiere.invoice.tax-1.0.0.jar
```

---

## 安裝

### 自動部署（推薦）

```bash
./deploy.sh
```

腳本自動執行：編譯 → 複製 JAR 到 plugins/ → 透過 OSGi telnet 熱更新 bundle。

### 手動部署

1. `mvn package`
2. 複製 `target/tw.idempiere.invoice.tax-*.jar` 到 iDempiere 的 `plugins/` 目錄
3. 重啟 iDempiere（或 OSGi console: `update <bundle_id> file://...`）
4. Bundle 啟動時自動執行 PackIn，安裝 4 張 TW_* 資料表、視窗、選單

### 首次安裝後

- 所有 active role 自動取得 TW 視窗讀寫權限（`afterPackIn()` 處理）
- **所有使用者需重新登入**才能看到新權限

---

## 核心架構

```
Bundle 啟動
  └─ TaiwanInvoiceTaxActivator (Incremental2PackActivator)
      └─ PackIn: 安裝 2Pack_1.0.11.zip
          └─ 安裝 AD_Table / AD_Window / AD_Field / AD_Menu 定義
      └─ afterPackIn(): 授予所有 active role 的 TW 視窗存取權限

資料模型（4張表）
  TW_InvoicePrefix      ← 字軌管理（AA, AB...）
  TW_Invoice_Prefix_Map ← 發票與字軌號碼對應
  TW_InvoiceAdjustment  ← 銷項/進項折讓追蹤
  TW_TaxStatement       ← 401 申報表

服務層（純 Java，可單元測試）
  InvoiceNumberingService  ← 發票號碼分配（SELECT FOR UPDATE）
  TaxCalculationService    ← 稅額計算（BigDecimal + FLOOR）
  MixedBusinessService     ← 兼營比例調整
  InvoiceValidationService ← 驗證規則（警告不阻擋）

事件處理器（AbstractEventHandler，OSGi DS 服務）
  InvoicePrefixEventHandler      ← 字軌格式、號碼範圍、狀態轉換驗證
  InvoicePrefixMapEventHandler   ← 發票日期、買方統一編號格式驗證
  InvoiceAdjustmentEventHandler  ← 折讓方向、期別驗證
  TaxStatementEventHandler       ← 申報年度、期別範圍驗證

驗證輔助（純靜態方法，非 OSGi 服務）
  InvoicePrefixValidator        ← 字軌驗證靜態方法
  InvoicePrefixMapValidator     ← 發票對應驗證靜態方法
  InvoiceAdjustmentValidator    ← 折讓驗證靜態方法
  TaxStatementValidator         ← 申報表驗證靜態方法

流程（SvrProcess）
  GenerateTaxStatementProcess ← 產生 401 申報表（查詢 TW_Invoice_Prefix_Map + TW_InvoiceAdjustment，建立 TW_TaxStatement）
  ExportTaxReportProcess      ← 匯出 TW_TaxStatement 為財政部電子申報 CSV
```

---

## 台灣法規核心規則

| 規則 | 說明 |
|------|------|
| 三聯式 (B2B) | 銷售額 × 1.05 = 含稅金額 |
| 二聯式 (B2C) | floor(含稅金額 ÷ 1.05) = 銷售額；稅額 = floor(銷售額 × 0.05) |
| 稅額計算 | 一律捨去法（FLOOR），不四捨五入（BigDecimal + RoundingMode.FLOOR） |
| 申報期間 | 雙月制：1-2月=第1期 … 11-12月=第6期 |
| 字軌狀態 | I（未啟用）→ A（使用中）→ C（已用完），C 不可逆 |
| 買方統一編號 | 三聯式（B2B）法定必填，須為 8 位純數字 |
| 進項折讓 | 必須當期申報；超期需使用者確認補稅風險 |
| 進項稅期限 | 10 年；90 天前警告 |
| 兼營調整 | 營業 < 9 個月免調整；比例 = 應稅銷售 / 總銷售（FLOOR） |
| 淨應納稅額 | 銷項稅 − (進項稅 − 不可扣抵進項稅) − 留抵稅額 |

---

## 文件

| 文件 | 說明 |
|------|------|
| `CLAUDE.md` | **必讀** — iDempiere 規則、domain 規則、禁止事項 |
| `docs/user-manual.md` | **使用手冊** — 4 個視窗操作說明、欄位意義、操作流程 |
| `docs/idempiere-plugin-dev-guide.md` | **開發指南** — 2Pack 規範、AD_Field 標準集合、除錯方法、升版策略 |
| `docs/schema/table-definitions.md` | 4 張 TW_* 資料表欄位、型別、業務規則 |
| `docs/schema/packout-column-reference.md` | PackOut.xml 欄位參照（AD_Reference_ID 對照） |

---

## 相關資源

- [財政部電子申報繳稅服務網](https://www.etax.nat.gov.tw/)
- [全國法規資料庫 — 加值型及非加值型營業稅法](https://law.moj.gov.tw/)
- [統一發票使用辦法](https://law.moj.gov.tw/)

---

## 變更記錄

### 2026-03-28 — v1.0.11：流程實作與完整端對端驗證

**流程實作：**
- **`GenerateTaxStatementProcess`**：查詢 `TW_Invoice_Prefix_Map`（銷售）+ `TW_InvoiceAdjustment`（折讓），彙總產生 `TW_TaxStatement`；防重複（同年期不重建）；FilingDueDate 自動計算
- **`ExportTaxReportProcess`**：查詢 `TW_TaxStatement` 並匯出 CSV（10 欄位）至 `/tmp/TW_TaxReport_YYYY_PN.csv`
- **`TaiwanProcessFactory`**：新增 `IProcessFactory` OSGi DS 元件，解決 `DefaultProcessFactory.newProcessInstance()` 跨 bundle `ClassNotFoundException`
- **`MTaxStatement`**：`getStatementPeriod()` / `setStatementPeriod()` 修正 `CHAR(1)` 型別轉換

**修復：**
- `clean_reinstall.sh`：加入 `AD_PInstance_Para` → `AD_PInstance_Log` → `AD_PInstance` 清除步驟
- `MANIFEST.MF`：加入 `org.adempiere.exceptions`、`org.osgi.service.component.annotations`

**端對端驗證（從零安裝全流程通過）：**
```
clean_reinstall.sh → 2Pack 1.0.11 安裝 → 建立字軌/發票/折讓 →
GenerateTaxStatementProcess → TW_TaxStatement 建立 →
ExportTaxReportProcess → CSV 匯出正確
```

---

### 2026-03-27 — v1.0.11：Grid View 修復

**關鍵修復：**
- **Index:2 Grid View 崩潰修復（v1.0.11）**：PackOut.xml 中所有 73 個 AD_Field 元素均缺少 `SeqNoGrid`。iDempiere 12 Grid renderer 需要 `SeqNoGrid > 0` 才能正確初始化行編輯器；缺少時，在任何 TW 視窗按「新增」就會拋出 `IndexOutOfBoundsException: Index: 2`。修復：顯示欄位設為 `SeqNoGrid = SeqNo`，隱藏欄位設為 `SeqNoGrid = 0`。2Pack 版本升至 1.0.11。

**附帶修復：**
- 雙 JVM 問題（兩個 iDempiere 實例互搶 port），透過 systemd restart 解決

---

### 2026-03-27 — 重大 Bug 修復與架構調整

**關鍵修復：**
- **事件主題比較方式修正**：`InvoicePrefixEventHandler` 將 `topic.endsWith("po_before_change")` 改為 `IEventTopics.PO_BEFORE_CHANGE.equals(topic)`，修復狀態轉換守衛在執行期靜默失效的問題
- **禁止 A→I 狀態降級**：`InvoicePrefixValidator` 新增攔截邏輯，禁止將使用中（A）字軌降回未啟用（I），符合台灣稅法規範
- **移除死程式碼**：`InvoicePrefixValidator` 與 `InvoicePrefixMapValidator` 移除 `implements ModelValidator` 及所有介面方法，改為純靜態輔助類（這些類從未被註冊為 OSGi 服務）

**重要修復：**
- **刪除過時 ZIP**：移除 `resources/2pack/tw_invoice_system.zip`（內容已過時）
- **刪除孤立 plugin.xml**：移除專案根目錄的 pipo 殘留檔案
- **修正 JaCoCo 規則**：移除參照錯誤套件和不存在類別的 per-class 覆蓋率規則
- **新增事件處理器**：加入 `InvoiceAdjustmentEventHandler` 與 `TaxStatementEventHandler`，各含驗證器與 OSGi component XML

**次要修復：**
- `calcGrossAmount()` Javadoc 更新（僅適用 B2B）
- `Activator` 中 `System.err.println` 改為 `logger.info`
- 4 個 Model 類別均新增 `COLUMNNAME_*_UU` 常數
- 新增 `AdjustmentDirection` 常數測試

**測試數量**：65 → 89 tests（全部通過）
