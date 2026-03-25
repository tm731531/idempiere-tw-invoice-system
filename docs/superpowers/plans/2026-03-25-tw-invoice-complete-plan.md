# Taiwan Invoice Tax System — Complete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以 iDempiere OSGi Plugin 的形式，實現符合台灣統一發票與營業稅法規的完整系統，透過 2Pack ZIP 安裝字典/UI 定義，透過 Model 層實現業務邏輯。

**Architecture:** Plugin 啟動時 Activator 呼叫 PackIn 灌入 2Pack ZIP（建立 AD_Table/Window/UI），Model 層實作業務規則，Service 層處理發票編號、稅額計算、兼營調整。兩段互相獨立：2Pack 管資料字典，Java 程式管業務邏輯。

**Tech Stack:** iDempiere 12.0 OSGi Bundle, Java 8+, PostgreSQL, Maven, JUnit 4, iDempiere PackIn API (`org.adempiere.pipo2`)

---

## 設計依據：台灣發票法規（Final Design v3.0）

> 來源：`Taiwan_Invoice_Tax_System_Final_Design_v3.md`（合規度 95/100）

### 4 張核心表

| 表名 | 用途 | 關鍵業務規則 |
|------|------|-------------|
| `TW_InvoicePrefix` | 字軌管理（AA, AB...） | 號碼範圍管理、Status A/I/C |
| `TW_Invoice_Prefix_Map` | 發票與字軌對應 | 日期順序警告（非阻擋）、月份一致性警告 |
| `TW_InvoiceAdjustment` | 進項折讓追蹤 | 當期申報強制、逾期警告 |
| `TW_TaxStatement` | 401 申報表（雙月） | 兼營9個月判定、應稅比例調整 |

### 核心業務規則

1. **三聯式**：銷售額 × 1.05 = 含稅金額
2. **二聯式**：含稅金額 ÷ 1.05 = 銷售額
3. **申報期間**：雙月制（1-6期），1-2月=第1期...11-12月=第6期
4. **發票日期驗證**：不能未來開立；同字軌日期應遞增（警告，不阻擋）
5. **發票月份驗證**：發票月份應與交易月份一致（警告，允許例外）
6. **進項折讓**：必須當期申報（當期 = 折讓發生的雙月期）
7. **進項稅期限**：10年到期追蹤，90天前警告
8. **兼營營業人**：年度 < 9個月免調整；比例 = 應稅銷售 / 總銷售；年底調整

---

## 現況盤點

| 元件 | 狀態 | 備注 |
|------|------|------|
| OSGi Bundle 骨架 | ✅ 完成 | pom.xml, MANIFEST.MF, OSGI-INF/component.xml |
| TaiwanInvoiceTaxActivator | ⚠️ 部分 | 存在但未實作 PackIn 邏輯 |
| MInvoicePrefix | ✅ 完成 | 含驗證邏輯 |
| MInvoicePrefixMap | ✅ 完成 | |
| MInvoiceAdjustment | ✅ 完成 | |
| MTaxStatement | ✅ 完成 | |
| AccountingCodeMapper | ✅ 完成 | |
| SQL Scripts (5個) | ✅ 完成 | 但 2Pack 才是 iDempiere 正式安裝方式 |
| 2Pack XML（4個） | ❌ 格式錯誤 | 需重寫為單一 PackOut.xml + ZIP |
| Business Service 層 | ❌ 未開始 | Phase 2 |
| Validator / Callout | ❌ 未開始 | Phase 3 |
| Process / Report | ❌ 未開始 | Phase 4 |
| AD_Window / UI 定義 | ❌ 未開始 | 在 2Pack 中定義 |

---

## 檔案結構（最終目標）

```
tw.idempiere.invoice.tax/
├── META-INF/MANIFEST.MF
├── OSGI-INF/component.xml
├── pom.xml
├── src/tw/idempiere/invoice/tax/
│   ├── TaiwanInvoiceTaxActivator.java       ← 加入 PackIn 邏輯
│   ├── model/
│   │   ├── MInvoicePrefix.java              ✅ 完成
│   │   ├── MInvoicePrefixMap.java           ✅ 完成
│   │   ├── MInvoiceAdjustment.java          ✅ 完成
│   │   └── MTaxStatement.java               ✅ 完成
│   ├── service/
│   │   ├── InvoiceNumberingService.java     ← Phase 2
│   │   ├── TaxCalculationService.java       ← Phase 2
│   │   ├── MixedBusinessService.java        ← Phase 2
│   │   └── InvoiceValidationService.java    ← Phase 2
│   ├── callout/
│   │   ├── InvoicePrefixCallout.java        ← Phase 3
│   │   └── TaxStatementCallout.java         ← Phase 3
│   ├── process/
│   │   ├── GenerateTaxStatementProcess.java ← Phase 4
│   │   └── ExportTaxReportProcess.java      ← Phase 4
│   └── util/
│       └── AccountingCodes.java             ✅ 完成
├── resources/
│   ├── 2pack/
│   │   └── tw_invoice_system.zip            ← Phase 1 重點：建立此 ZIP
│   │       └── tw_invoice_system/dict/
│   │           └── PackOut.xml              ← 唯一的 2Pack 字典定義檔
│   └── sql/                                 ✅ 5個 SQL 腳本已存在
└── test/tw/idempiere/invoice/tax/
    ├── model/*Test.java                     ✅ 完成
    ├── service/*Test.java                   ← Phase 2
    └── IntegrationTest.java                 ✅ 完成
```

---

## Phase 0：修正 2Pack（最高優先）

> **目的**：建立符合 iDempiere PackIn 格式的 2Pack ZIP，讓 Activator 能自動安裝字典定義

### Task 0.1：建立正確的 PackOut.xml

**關鍵格式規則（來自 iDempiere 原始碼研究）：**
- 根元素 `<idempiere Name="..." Version="..." ...>` 需有完整屬性
- 布林值：`Y`/`N`（IsActive 例外用 `true`/`false`）
- FK 引用：`<AD_Reference_ID reference="id" reference-key="AD_Reference">13</AD_Reference_ID>`
- 自訂 Reference：`<AD_Reference_Value_ID reference="uuid" reference-key="AD_Reference">uuid</AD_Reference_Value_ID>`
- 每筆記錄需有穩定 UUID（`AD_Table_UU`, `AD_Column_UU` 等）
- 所有定義在單一 `PackOut.xml`，先定義 AD_Reference，再定義 AD_Table
- 包含 AD_Window / AD_Tab / AD_Field UI 定義

**Files:**
- Delete: `2pack/TW_InvoicePrefix.xml`, `TW_Invoice_Prefix_Map.xml`, `TW_InvoiceAdjustment.xml`, `TW_TaxStatement.xml`
- Create: `resources/2pack/tw_invoice_system/dict/PackOut.xml`

- [ ] **Step 1: 生成所有需要的穩定 UUID**

```bash
# 為每個 AD_Reference, AD_Table, AD_Column, AD_Window, AD_Tab, AD_Field 生成 UUID
python3 -c "import uuid; [print(uuid.uuid4()) for _ in range(80)]" > /tmp/uuids.txt
```

- [ ] **Step 2: 撰寫 PackOut.xml 根元素與 AD_EntityType**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<idempiere
    Name="TW Invoice Tax System"
    Version="1.0.0"
    idempiereVersion="12.0.0"
    DataBaseVersion="12.0.0"
    Description="Taiwan Unified Invoice and Business Tax System"
    Author="TW iDempiere Community"
    AuthorEmail="dev@tw-idempiere.org"
    CreatedDate="2026-03-25 00:00:00"
    UpdatedDate="2026-03-25 00:00:00"
    PackOutVersion="100"
    UpdateDictionary="true"
    Client="0"
    AD_Client_UU="11111111-1111-1111-1111-111111111111">

  <AD_EntityType>
    <EntityType>TW</EntityType>
    <Name>Taiwan Invoice System</Name>
    <Description>Taiwan Unified Invoice and Business Tax</Description>
    <IsActive>Y</IsActive>
    <AD_EntityType_UU><!-- stable uuid --></AD_EntityType_UU>
  </AD_EntityType>
```

- [ ] **Step 2.5（前置）：確認 C_Org_ID vs AD_Org_ID 欄位名稱**

> **重要（reviewer 發現）**：Final Design v3 SQL 使用 `C_Org_ID`，但 iDempiere 標準欄位名是 `AD_Org_ID`（Reference Type 19 = TableDir）。現有 SQL Scripts 與 Model 使用哪個名稱，PackOut.xml 的 AD_Column ColumnName 必須與 Java 常數 `COLUMNNAME_*` 完全一致。

```bash
# 確認現有 SQL scripts 使用的欄位名稱
grep -n "Org_ID" src/main/resources/sql/*.sql
grep -n "COLUMNNAME.*Org" src/tw/idempiere/invoice/tax/model/*.java
```

預期：兩者都用 `AD_Org_ID`（如有不一致，以 Model Java 常數為準）

- [ ] **Step 3: 加入 AD_Reference 定義（4個）**
  - `TW_InvoiceType`：SALES_TRIPART / SALES_BIPART
  - `TW_InvoicePrefixStatus`：A（Active）/ I（Inactive）/ C（Complete）
  - `TW_AdjustmentType`：RETURN / ALLOWANCE
  - `TW_TaxPeriod`：1–6

- [ ] **Step 4: 加入 4 張 AD_Table + AD_Column 定義**

注意：
- PK 欄位：`<AD_Reference_ID reference="id" reference-key="AD_Reference">13</AD_Reference_ID>`（ID type）
- List 欄位：`<AD_Reference_ID ...>17</AD_Reference_ID>` + `<AD_Reference_Value_ID reference="uuid" ...>ref-uuid</AD_Reference_Value_ID>`
- FK 欄位：`<AD_Reference_ID ...>30</AD_Reference_ID>`（Search type）+ `<AD_Reference_Value_ID reference="uuid" ...>`
- IsActive/IsMandatory 等布林欄位用 `Y`/`N`
- **ColumnName 必須與 Model 的 `COLUMNNAME_*` 常數完全一致**（見 Step 2.5）

- [ ] **Step 5: 加入 UI 定義（AD_Window / AD_Tab / AD_Field + AD_Menu）**

> **重要（reviewer 發現）**：必須加入 `AD_Menu` 條目，否則用戶無法透過 iDempiere 選單找到視窗。每個 AD_Window 對應一個 AD_Menu 條目。

```xml
  <!-- 字軌管理視窗 -->
  <AD_Window>
    <Name>TW Invoice Prefix</Name>
    <Description>台灣發票字軌管理</Description>
    <WindowType>M</WindowType><!-- M=Maintain -->
    <IsActive>Y</IsActive>
    <EntityType>TW</EntityType>
    <AD_Window_UU><!-- stable uuid --></AD_Window_UU>

    <AD_Tab>
      <Name>TW Invoice Prefix</Name>
      <AD_Table_UU><!-- TW_InvoicePrefix table uuid --></AD_Table_UU>
      <SeqNo>10</SeqNo>
      <TabLevel>0</TabLevel>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Tab_UU><!-- stable uuid --></AD_Tab_UU>

      <AD_Field>
        <Name>PrefixCode</Name>
        <AD_Column_UU><!-- PrefixCode column uuid --></AD_Column_UU>
        <SeqNo>10</SeqNo>
        <IsActive>Y</IsActive>
        <EntityType>TW</EntityType>
        <AD_Field_UU><!-- stable uuid --></AD_Field_UU>
      </AD_Field>
      <!-- ... 其他欄位 ... -->
    </AD_Tab>
  </AD_Window>
```

- [ ] **Step 6: 驗證 XML 格式**

```bash
xmllint --noout resources/2pack/tw_invoice_system/dict/PackOut.xml
echo "XML validation: $?"
```

- [ ] **Step 7: 打包成 ZIP**

```bash
cd resources/2pack
zip -r tw_invoice_system.zip tw_invoice_system/
# 驗證 ZIP 結構
unzip -l tw_invoice_system.zip
# 預期輸出：tw_invoice_system/dict/PackOut.xml
```

- [ ] **Step 8: Commit**

```bash
git add resources/2pack/
git rm 2pack/  # 刪除舊的錯誤格式 XML
git commit -m "feat: add proper iDempiere 2Pack ZIP with PackOut.xml"
```

---

### Task 0.2：更新 Activator 執行 PackIn

**Files:**
- Modify: `src/tw/idempiere/invoice/tax/TaiwanInvoiceTaxActivator.java`

- [ ] **Step 1: 寫 PackIn 邏輯測試（先驗概念）**

```java
// test/tw/idempiere/invoice/tax/ActivatorPackInTest.java
@Test
public void testPackInResourceExists() {
    URL zipUrl = TaiwanInvoiceTaxActivator.class
        .getResource("/2pack/tw_invoice_system.zip");
    assertNotNull("2Pack ZIP must exist in bundle resources", zipUrl);
}
```

- [ ] **Step 2: 實作 Activator 的 PackIn 邏輯**

> **重要（reviewer 修正）**：`PackInProcess` 是 `SvrProcess` 子類，不能直接 `new` 使用。正確 API 是 `PackIn.importXML()`。也需要在 `Import-Package` 加入 `org.adempiere.pipo2`（見 Step 3）。

```java
import org.adempiere.pipo2.PackIn;

@Override
public void start(BundleContext context) throws Exception {
    bundleContext = context;
    log.info("=== Taiwan Invoice Tax System Bundle Starting ===");
    installDictionary(context);
}

private void installDictionary(BundleContext context) {
    try {
        URL zipUrl = context.getBundle().getResource("2pack/tw_invoice_system.zip");
        if (zipUrl == null) {
            log.warning("2Pack ZIP not found in bundle resources — skipping dictionary install");
            return;
        }
        // Copy ZIP to temp file
        File tempZip = File.createTempFile("tw_invoice_system", ".zip");
        try (InputStream in = zipUrl.openStream();
             FileOutputStream out = new FileOutputStream(tempZip)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        // Run PackIn via correct API
        PackIn packIn = new PackIn();
        packIn.importXML(tempZip.getAbsolutePath(), Env.getCtx(), null);
        log.info("Taiwan Invoice Tax System 2Pack installed successfully.");
        tempZip.delete();
    } catch (Exception e) {
        log.log(Level.WARNING, "2Pack install failed (tables may already exist): " + e.getMessage());
        // Non-fatal: tables might already be installed from a previous startup
    }
}
```

- [ ] **Step 3: 更新 MANIFEST.MF — 加入 `org.adempiere.pipo2` Import**

> **重要（reviewer 發現）**：沒有這個 import，OSGi 會拒絕解析 `PackIn` 類，bundle 啟動失敗。

在 `META-INF/MANIFEST.MF` 的 `Import-Package` 加入：
```
Import-Package: ...,
 org.adempiere.pipo2;version="[12.0.0,13.0.0)"
```

同時確認 Bundle-ClassPath 包含資源路徑（2pack ZIP 能被 bundle 讀取）：
```
Bundle-ClassPath: .,resources/
```

- [ ] **Step 4: 編譯確認**

```bash
cd /home/tom/idempiere-tw-invoice-system
mvn compile
```

- [ ] **Step 5: Commit**

```bash
git add src/tw/idempiere/invoice/tax/TaiwanInvoiceTaxActivator.java
git commit -m "feat: add PackIn invocation in bundle Activator for 2Pack installation"
```

---

## Phase 1（已完成）✅

以下元件已在 `29602b1` commit 完成：

- `MInvoicePrefix.java` — 字軌管理（PrefixCode 2字母驗證、號碼範圍驗證）
- `MInvoicePrefixMap.java` — 發票字軌對應（月份/日期順序警告）
- `MInvoiceAdjustment.java` — 進項折讓（當期申報追蹤）
- `MTaxStatement.java` — 401 申報表（兼營比例計算）
- `AccountingCodeMapper.java` — 會計科目動態映射
- SQL Scripts × 5
- Integration Tests（80%+ coverage）

**Phase 1 審查項目（可複查）：**
- [ ] SQL 欄位與 PackOut.xml AD_Column 定義一致（Column Name 需對齊）
- [ ] Model 的常數 COLUMNNAME_* 與 2Pack 中 ColumnName 欄位一致

---

## Phase 2：業務邏輯服務層

### Task 2.1：InvoiceNumberingService — 發票號碼分配

**Files:**
- Create: `src/.../service/InvoiceNumberingService.java`
- Create: `test/.../service/InvoiceNumberingServiceTest.java`

核心邏輯：
1. 根據 B2B（有統編）→ 三聯式，B2C（無統編）→ 二聯式
2. 鎖定 TW_InvoicePrefix（`SELECT FOR UPDATE`），分配下一個號碼
3. 格式化：`PrefixCode + 8位序號`（如 AA00000001）
4. 檢查是否已用完（CurrentNumber > EndNumber → Status=C，找下一個 Active 字軌）

- [ ] **Step 1: 寫失敗測試**

```java
@Test
public void testNextInvoiceNumber_tripart() {
    // Arrange: prefix AA, start=1, end=999999, current=1
    // Act: allocate next number
    // Assert: returns "AA00000001", current becomes 2
}

@Test
public void testAutoSelectNextPrefix_whenCurrent_complete() {
    // Arrange: prefix AA complete, prefix AB active
    // Act: request tripart number
    // Assert: returns AB prefix number
}
```

- [ ] **Step 2: 確認測試失敗**
- [ ] **Step 3: 實作 InvoiceNumberingService**
- [ ] **Step 4: 確認測試通過**

```bash
mvn test -Dtest=InvoiceNumberingServiceTest
```

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add InvoiceNumberingService with prefix auto-selection"
```

---

### Task 2.2：TaxCalculationService — 稅額計算

核心邏輯：
1. 三聯式：`taxAmount = floor(saleAmount × 0.05)`（**台灣稅務：捨去法，非四捨五入**）
2. 二聯式（含稅反推）：`saleAmount = floor(grossAmount / 1.05)`, `taxAmount = grossAmount - saleAmount`
3. 申報期間：`period = (month - 1) / 2 + 1`（1-2月=1期, 3-4月=2期...）

使用 `BigDecimal` + `RoundingMode.FLOOR`，不使用 `Math.round()`。

- [ ] **Step 1-5: TDD 流程（同上）**

```java
@Test
public void testTripartTaxCalculation() {
    // 100001 × 0.05 = 5000.05 → 捨去 → 5000（不是四捨五入的5001）
    assertEquals(new BigDecimal("5000"),
        TaxCalculationService.calcOutputTax(new BigDecimal("100001"), InvoiceType.TRIPART));
}

@Test
public void testBipartReverseTax() {
    // 105000 含稅 → 銷售額 = floor(105000/1.05) = 100000, 稅額 = 5000
    TaxResult r = TaxCalculationService.calcFromGross(new BigDecimal("105000"), InvoiceType.BIPART);
    assertEquals(new BigDecimal("100000"), r.getSaleAmount());
    assertEquals(new BigDecimal("5000"), r.getTaxAmount());
}

@Test
public void testReportingPeriod() {
    assertEquals(1, TaxCalculationService.getReportingPeriod(Month.JANUARY));
    assertEquals(1, TaxCalculationService.getReportingPeriod(Month.FEBRUARY));
    assertEquals(3, TaxCalculationService.getReportingPeriod(Month.MAY));
    assertEquals(6, TaxCalculationService.getReportingPeriod(Month.DECEMBER));
}
```

---

### Task 2.3：MixedBusinessService — 兼營營業人

核心邏輯：
1. `isMixedBusiness(orgId)` — 檢查是否為兼營
2. `isEligibleForAdjustment(operatingMonths)` — < 9個月 → false（免調整）
3. `calcTaxableRatio(taxableRevenue, totalRevenue)` → 比例（0.0000-1.0000）
4. `adjustInputTax(inputTax, ratio)` → 可扣抵進項稅額

```java
@Test
public void testLessThan9Months_noAdjustmentNeeded() {
    assertFalse(MixedBusinessService.isEligibleForAdjustment(8));
}

@Test
public void testTaxableRatioCalculation() {
    BigDecimal ratio = MixedBusinessService.calcTaxableRatio(
        new BigDecimal("800000"), new BigDecimal("1000000"));
    assertEquals(new BigDecimal("0.8000"), ratio);
}
```

---

### Task 2.4：InvoiceValidationService — 驗證規則

核心邏輯（均為警告，不阻擋業務）：
1. `validateInvoiceDate(date)` — date <= today
2. `validateDateSequence(prefixId, date)` — 同字軌最後日期比較
3. `validateMonthConsistency(invoiceDate, trxDate)` — 月份比較
4. `validateAdjustmentPeriod(adjustmentDate, reportingPeriod)` — 當期申報

```java
@Test
public void testFutureDateRejected() {
    ValidationResult r = InvoiceValidationService.validateInvoiceDate(
        LocalDate.now().plusDays(1));
    assertFalse(r.isValid());
}

@Test
public void testDateSequenceWarning_notBlocking() {
    // 非遞增日期 → 警告，但 isValid() 仍為 true
    ValidationResult r = InvoiceValidationService.validateDateSequence(
        prefixId, olderDate);
    assertTrue(r.isValid());  // 不阻擋
    assertTrue(r.hasWarning());
}
```

- [ ] **Commit Phase 2**

```bash
git commit -m "feat: add Phase 2 service layer - numbering, tax calc, mixed business, validation"
```

---

## Phase 3：Validator + Callout 層

### Task 3.1：InvoicePrefixValidator（@ModelEvent）

監聽 `TW_InvoicePrefix` 儲存事件，執行：
- PrefixCode 格式驗證（2大寫字母）
- StartNumber / EndNumber 合理性
- Status 轉換規則（C → 不可再改為 A）

```java
@ModelEvent(tableName = MInvoicePrefix.Table_Name,
            type = {ModelValidationEngine.TIMING_BEFORE_NEW,
                    ModelValidationEngine.TIMING_BEFORE_CHANGE})
public String onSave(PO po, int timing) {
    MInvoicePrefix prefix = (MInvoicePrefix) po;
    // ... validation logic
}
```

### Task 3.2：InvoicePrefixCallout

監聽 `C_Invoice` 的 `C_BPartner_ID` 欄位變更，自動選擇字軌類型（三聯/二聯）。

### Task 3.3：TaxStatementCallout

監聽申報表的 `TaxYear` / `TaxPeriod` 變更，自動彙總對應期間的銷項/進項數據。

- [ ] **Commit Phase 3**

```bash
git commit -m "feat: add Phase 3 validators and callouts for invoice prefix and tax statement"
```

---

## Phase 4：Process 層

### Task 4.1：GenerateTaxStatementProcess

流程：根據選定的年度和期間，彙整：
- 銷項稅（from TW_Invoice_Prefix_Map）
- 進項折讓（from TW_InvoiceAdjustment）
- 兼營比例計算（from MixedBusinessService）
- 計算 TaxPayable = OutputTax - AdjustedInputTax
- 建立 TW_TaxStatement 記錄

### Task 4.2：ExportTaxReportProcess

產生符合財政部格式的申報資料（CSV/XML 匯出）。

- [ ] **Commit Phase 4**

```bash
git commit -m "feat: add Phase 4 tax statement generation and export processes"
```

---

## Phase 5：最終整合與驗證

### Task 5.1：Bundle 完整編譯測試

```bash
mvn clean package
# 預期：BUILD SUCCESS
# 確認 JAR 包含：
jar tf target/tw.idempiere.invoice.tax-1.0.0-SNAPSHOT.jar | grep -E "(2pack|PackOut)"
```

### Task 5.2：2Pack 匯入驗證（手動）

在 iDempiere 環境中：
1. 部署 bundle JAR
2. 觀察 Felix 控制台 log：`Taiwan Invoice Tax System 2Pack installed successfully`
3. 在 iDempiere → Application Dictionary → Tables → 確認 `TW_InvoicePrefix` 等表存在
4. 在視窗功能 → 確認 `TW Invoice Prefix` 視窗已建立

### Task 5.3：覆蓋率確認

```bash
mvn test jacoco:report
# 目標：核心業務邏輯 > 80%
open target/site/jacoco/index.html
```

- [ ] **Final Commit**

```bash
git commit -m "feat: complete Taiwan invoice tax system Phase 5 integration"
git tag v1.0.0
```

---

## 台灣發票合規性確認清單

執行計畫前，請確認設計符合以下規定：

### 字軌與發票號碼
- [ ] 字軌 2 字母（AA-ZZ），號碼 8 位（00000001-99999999）
- [ ] 三聯式（B2B，有統編）/ 二聯式（B2C，無統編）正確區分
- [ ] 字軌耗盡時自動切換下一個 Active 字軌

### 稅額計算
- [ ] 三聯式：稅率 5%，`稅額 = round(銷售額 × 0.05)`
- [ ] 二聯式：`銷售額 = round(含稅金額 / 1.05)`

### 申報期間
- [ ] 雙月制：1-2月=第1期, 3-4月=第2期, 5-6月=第3期, 7-8月=第4期, 9-10月=第5期, 11-12月=第6期

### 進項折讓
- [ ] 必須當期申報（與折讓發生同一雙月期）
- [ ] 逾期標記 `IsReportedOnTime=N` 並顯示警告

### 進項稅期限
- [ ] 10 年期限追蹤
- [ ] 到期前 90 天顯示 `IsExpiryWarning=Y`

### 兼營營業人
- [ ] 年度 < 9 個月：免進行比例調整
- [ ] 比例 = 應稅銷售額 / 總銷售額（含應稅+免稅）
- [ ] 年底調整：以全年實際比例重算

---

## 執行選項

**Plan complete. Two execution options:**

**1. Subagent-Driven（推薦）** — 每個 Task 派新的 subagent 執行，每 Task 後進行 code review

**2. Inline Execution** — 在本 session 使用 executing-plans skill 逐 Task 執行

先從 **Phase 0（2Pack 重建）** 開始，因為這是整個 plugin 安裝機制的基礎。
