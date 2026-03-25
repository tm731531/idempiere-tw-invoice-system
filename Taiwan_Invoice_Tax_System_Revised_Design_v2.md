# 台灣統一發票與營業稅系統 - 修正設計 v2.0

**日期**: 2026-03-24
**狀態**: 待網路驗證
**目標**: 基於財會與稅務專家審查清單，修正6項重大設計缺陷 + 新增4項缺失規則

---

## 背景與修正內容

### 修正的6項重大缺陷
1. ✅ 三聯式 vs 二聯式金額計算差異
2. ✅ 進項稅折讓必須當期申報（無例外）
3. ✅ 兼營營業人的年度調整與比例扣抵
4. ✅ 發票開立日期決定申報期間（非收款日期）
5. ✅ 含稅 vs 不含稅金額的自動轉換
6. ✅ 跨年度發票10年期限管理

### 新增的4項缺失規則
1. ✅ **發票日期順序驗證** - 同一字軌的發票必須按日期遞增
2. ✅ **兼營營業人9個月判定** - 當年度未滿9個月應免調整，併入次年
3. ✅ **進項稅10年期限提醒** - 超過10年自動警告與鎖定
4. ✅ **發票月份一致性檢查** - 發票月份必須與交易月份一致

---

## I. 核心表結構設計（修正版）

### 1. TW_InvoicePrefix - 發票字軌管理表

```sql
CREATE TABLE TW_InvoicePrefix (
    -- 基本資訊
    TW_InvoicePrefix_ID    BIGINT PRIMARY KEY,
    C_Org_ID              BIGINT NOT NULL (組織),
    PrefixCode            VARCHAR(5) NOT NULL (字軌代碼: AA, AB, AC等),
    InvoiceType           VARCHAR(20) NOT NULL (SALES_TRIPART, SALES_BIPART, PURCHASE),

    -- 號碼管理
    CurrentNumber         BIGINT (當前已用號碼),
    StartNumber           BIGINT (起始號碼),
    EndNumber             BIGINT (終止號碼),

    -- 日期管理
    PrefixStartDate       DATE (字軌啟用日期),
    PrefixEndDate         DATE (字軌停用日期),

    -- 狀態管理
    Status                CHAR(1) (A=活跃, I=停用, C=完成),

    -- ⭐ 新增: 發票日期順序驗證
    LastIssuedInvoiceDate DATE (最後開立發票日期),
    LastInvoiceNumber     VARCHAR(20) (最後發票號碼),

    -- 審計
    Created               TIMESTAMP,
    Modified              TIMESTAMP
);
```

### 2. TW_Invoice_Prefix_Map - 發票與字軌關聯表

```sql
CREATE TABLE TW_Invoice_Prefix_Map (
    -- 基本關聯
    TW_Invoice_Prefix_Map_ID BIGINT PRIMARY KEY,
    C_Invoice_ID              BIGINT NOT NULL (FK),
    TW_InvoicePrefix_ID       BIGINT NOT NULL (FK),

    -- 發票編號
    InvoiceNumber             VARCHAR(20) NOT NULL (完整發票號: AA2600001),
    InvoiceSequence           BIGINT (字軌內序號: 1, 2, 3...),

    -- 發票類型與金額處理
    InvoiceType               VARCHAR(20) (TRIPART/BIPART),
    IsUnitPriceIncludeTax     CHAR(1) (Y/N - 單價是否含稅),

    -- 稅務認列日期與申報期間
    DateInvoiced              DATE (發票開立日期 - 決定申報期間),
    ActualReportingPeriod     INT (1-6 表示6個期間),

    -- ⭐ 新增: 發票月份一致性驗證
    InvoiceMonth              INT (1-12 發票開立月份),
    TransactionMonth          INT (1-12 交易月份),
    IsMonthConsistent         CHAR(1) (Y/N - 月份是否一致),
    MonthMismatchReason       VARCHAR(255) (不一致時的說明),

    -- ⭐ 新增: 發票日期順序驗證
    PreviousInvoiceDateInPrefix DATE (同字軌上一張發票日期),
    IsDateSequenceValid         CHAR(1) (Y/N - 日期是否遞增),
    DateSequenceWarning         VARCHAR(255) (日期順序警告),

    -- 10年期限管理
    DaysUntilExpiry           INT (距離期限還有幾天),
    IsExpiringWarning         CHAR(1) (Y/N - 是否即將過期),
    ExpiryDate                DATE (10年期限到期日),

    -- 審計
    Created                   TIMESTAMP,
    Modified                  TIMESTAMP
);
```

### 3. TW_InvoiceAdjustment - 進項發票折讓管理表

```sql
CREATE TABLE TW_InvoiceAdjustment (
    -- 基本資訊
    TW_InvoiceAdjustment_ID BIGINT PRIMARY KEY,
    C_Invoice_ID            BIGINT NOT NULL (原始進項發票FK),
    C_InvoiceLine_ID        BIGINT (折讓行項FK),

    -- 折讓明細
    AdjustmentAmount        DECIMAL(19,2) (折讓金額),
    AdjustmentTax           DECIMAL(19,2) (折讓稅額),

    -- 申報管理
    OriginalInvoiceDate     DATE (原始發票日期),
    AdjustmentDate          DATE (折讓開立日期),

    -- ⭐ 重點: 強制當期申報（無例外）
    RequiredReportingPeriod INT (1-6 必須在此期間申報),
    IsReportedOnTime        CHAR(1) (Y/N 是否按時申報),
    ReportedInPeriod        INT (1-6 實際申報期間),
    OverdueWarning          VARCHAR(255) (超期警告信息),

    -- 審計
    Created                 TIMESTAMP,
    Modified                TIMESTAMP
);
```

### 4. TW_TaxStatement - 營業稅申報表（修正版）

```sql
CREATE TABLE TW_TaxStatement (
    -- 申報基本資訊
    TW_TaxStatement_ID        BIGINT PRIMARY KEY,
    C_Org_ID                  BIGINT NOT NULL,
    TaxYear                   INT (西元年),
    TaxPeriod                 INT (1-6 表示6個期間),
    ReportingDeadline         DATE (申報截止日期),

    -- 銷項稅 (Sales)
    TaxableSalesAmount        DECIMAL(19,2) (應稅銷售額),
    TaxFreeSalesAmount        DECIMAL(19,2) (免稅銷售額),
    ZeroRatedSalesAmount      DECIMAL(19,2) (零稅率銷售額),
    OutputTax                 DECIMAL(19,2) (銷項稅 = 應稅銷售額 × 5%),

    -- 進項稅 (Purchases)
    DeductibleInputTax        DECIMAL(19,2) (可扣抵進項稅),
    NonDeductibleInputTax     DECIMAL(19,2) (不可扣抵進項稅),

    -- 進項折讓管理
    AdjustedInputTax          DECIMAL(19,2) (進項稅折讓金額),
    AdjustmentCount           INT (本期折讓筆數),
    OverdueAdjustmentCount    INT (超期未申報的折讓筆數),

    -- ⭐ 兼營營業人稅額調整
    MixedTaxableMode          VARCHAR(20) (RATIO=比例法, DIRECT=直接法),
    ActualTaxableRatio        DECIMAL(5,2) (實際應稅比例),
    RatioAdjustedInputTax     DECIMAL(19,2) (比例調整後進項稅),

    -- ⭐ 新增: 9個月判定規則
    HasMinimum9MonthsOfMixedBusiness CHAR(1) (Y/N - 本年度是否滿9個月兼營),
    OperatingMonthsInYear     INT (本年度營業月數),
    IsEligibleForAdjustment   CHAR(1) (Y/N - 是否符合年度調整條件),
    DeferAdjustmentToNextYear CHAR(1) (Y/N - 是否應延期至次年調整),

    -- 年度調整
    IsYearEndAdjustment       CHAR(1) (Y/N - 是否為年度調整期),
    TaxAdjustment             DECIMAL(19,2) (年度調整金額),
    AdjustedTax               DECIMAL(19,2) (調整後應繳稅額),
    TaxPayable                DECIMAL(19,2) (應納稅額 = 銷項稅 - 可扣抵進項稅),
    TaxRefundable             DECIMAL(19,2) (應退稅額),

    -- ⭐ 新增: 10年期限管理
    InputTaxExpiryTrackingList TEXT (JSON格式的進項稅期限追蹤列表),
    ExpiringInvoiceCount      INT (即將過期的進項發票筆數),
    ExpiredInvoiceCount       INT (已過期的進項發票筆數),

    -- ⭐ 警告與提示
    OverdueWarning            VARCHAR(255) (超期警告),
    MissingAdjustmentWarning  VARCHAR(255) (漏報折讓警告),
    DateValidationWarning     VARCHAR(255) (日期驗證警告),

    -- 狀態
    Status                    VARCHAR(20) (DRAFT=草稿, SUBMITTED=已申報, APPROVED=已核定),

    -- 審計
    Created                   TIMESTAMP,
    Modified                  TIMESTAMP
);
```

---

## II. 業務邏輯規則（修正版）

### Rule 1: 發票開立日期決定申報期間

```
發票開立日期 (DateInvoiced) 範圍 → 適用申報期間

【銷售發票】
1月1日 ~ 2月28日 → 第1期（截止3月15日）
3月1日 ~ 4月30日 → 第2期（截止5月15日）
5月1日 ~ 6月30日 → 第3期（截止7月15日）
7月1日 ~ 8月31日 → 第4期（截止9月15日）
9月1日 ~ 10月31日 → 第5期（截止11月15日）
11月1日 ~ 12月31日 → 第6期（截止1月15日次年）

【進項發票】
同上規則適用

✅ 確認: 使用DateInvoiced（發票開立日期），NOT DateAcct（會計日期）或DueDate（收款日期）
```

### Rule 2: 三聯式 vs 二聯式金額計算

```
【三聯式 (Tripart) - B2B】
- 開立對象: 客戶有統一編號的公司
- 金額顯示: 不含稅金額
- 稅額計算: 銷售額 × 5% = 稅額
- 公式: 銷售額 + 稅額 = 含稅金額
- 例: 銷售額1000 → 稅額50 → 含稅金額1050

【二聯式 (Bipart) - B2C】
- 開立對象: 消費者（無統編）
- 金額顯示: 含稅金額
- 稅額計算: 含稅金額 ÷ 1.05 = 銷售額; 銷售額 × 5% = 稅額
- 公式: 含稅金額 = 銷售額 + 稅額
- 例: 含稅金額1050 → 銷售額1000 → 稅額50

⚠️ IsUnitPriceIncludeTax字段:
- Y = 單價含稅（二聯式或特殊情況）
- N = 單價不含稅（三聯式標準）
```

### Rule 3: 進項稅折讓強制當期申報

```
進項稅折讓 (Input Tax Allowance) 規則:

1. 折讓開立日期 → RequiredReportingPeriod 計算
   計算方式: 與原始發票的DateInvoiced 同期間申報

2. 強制當期申報 (NO EXCEPTION)
   - 不可延期
   - 不可合併申報
   - 必須在 RequiredReportingPeriod 內申報

3. 超期檢查
   IF ReportedInPeriod > RequiredReportingPeriod:
      → OverdueWarning = "進項稅折讓超期申報 (超過<期數>期)"
      → IsReportedOnTime = 'N'
      → 自動扣減該期進項稅（國稅局規定）

4. 系統操作流程:
   a) 建立折讓單 (C_Invoice, Adjustment Document)
   b) 自動計算 RequiredReportingPeriod
   c) 在期限到期前提示會計人員
   d) 如超期，自動標記並警告
```

### Rule 4: 發票日期順序驗證

```
同一字軌內的發票，開立日期必須遞增：

驗證邏輯:
  FOR EACH TW_Invoice_Prefix_Map WHERE TW_InvoicePrefix_ID = X
  ORDER BY DateInvoiced

  IF (CurrentInvoice.DateInvoiced < PreviousInvoice.DateInvoiced):
    IsDateSequenceValid = 'N'
    DateSequenceWarning = "發票日期不遞增: 前一張<前一張日期>, 本張<本張日期>"
    ALERT("⚠️ 發票日期順序錯誤")
  ELSE:
    IsDateSequenceValid = 'Y'
    PreviousInvoiceDateInPrefix = CurrentInvoice.DateInvoiced

系統動作:
- 保存發票時，自動檢查 PreviousInvoiceDateInPrefix
- 若不符合，則:
  ✓ 允許保存但標記為警告
  ✓ 提示會計人員稽查
  ✓ 報告時註記不符
```

### Rule 5: 兼營營業人年度調整與9個月規則

```
兼營營業人 (Mixed Business) - 比例扣抵法:

1. 判定條件 (年初時)
   IF 本年度銷售額既含應稅又含免稅 → MixedTaxableMode = 'RATIO'

2. 比例計算 (每月或每期)
   ActualTaxableRatio = 應稅銷售額 / (應稅銷售額 + 免稅銷售額)

   例: 應稅100萬 + 免稅50萬
       比例 = 100 / (100+50) = 66.67%

3. 進項稅調整
   RatioAdjustedInputTax = 本期進項稅 × ActualTaxableRatio

   例: 進項稅100萬 × 66.67% = 66.67萬 (可扣)
       不可扣部分 = 100萬 × 33.33% = 33.33萬

4. ⭐ 年度調整與9個月規則 (新增!)

   IF 本年度營業月數 < 9個月:
      HasMinimum9MonthsOfMixedBusiness = 'N'
      IsEligibleForAdjustment = 'N'
      DeferAdjustmentToNextYear = 'Y'
      → 不進行年度調整，併入次年第一期調整

   ELSE IF 本年度營業月數 >= 9個月:
      HasMinimum9MonthsOfMixedBusiness = 'Y'
      IsEligibleForAdjustment = 'Y'
      DeferAdjustmentToNextYear = 'N'
      IsYearEndAdjustment = 'Y' (第6期)
      → 在第6期（年末）進行年度調整

5. 年度調整計算 (第6期)

   IF IsYearEndAdjustment = 'Y':
      全年實際應稅比例 = 全年應稅銷售額 / 全年總銷售額
      全年應調進項稅 = 全年進項稅 × 全年實際應稅比例

      TaxAdjustment = 全年應調進項稅 - (各期已扣進項稅和)

      IF TaxAdjustment > 0:
         TaxRefundable = TaxAdjustment (應退稅)
      ELSE:
         AdjustedTax = ABS(TaxAdjustment) (應補稅)
```

### Rule 6: 發票月份一致性驗證

```
發票月份 (InvoiceMonth) 必須與交易月份 (TransactionMonth) 一致:

驗證邏輯:
  InvoiceMonth = MONTH(DateInvoiced)
  TransactionMonth = MONTH(C_Invoice.DateTrx 或 C_InvoiceLine.DateTrx)

  IF InvoiceMonth != TransactionMonth:
    IsMonthConsistent = 'N'
    MonthMismatchReason = "發票月份<發票月>與交易月份<交易月>不一致"
    ALERT("⚠️ 發票月份與交易月份不符")
  ELSE:
    IsMonthConsistent = 'Y'

例外情況 (需要說明):
  - 跨月份交易（分期發票）
  - 預開發票（需稅務機關核准）
  - 修正發票（補開或作廢重開）

系統動作:
- 允許建立但需提示/需要會計人員確認
- 在稅務申報時特別標註
```

### Rule 7: 進項稅10年期限管理

```
進項發票可扣抵期限: 開立日期後 10 年內

追蹤邏輯:
  ForEach 進項發票:
    ExpiryDate = DateInvoiced + 10 年
    DaysUntilExpiry = ExpiryDate - TODAY()

    IF DaysUntilExpiry <= 30 AND DaysUntilExpiry > 0:
      IsExpiringWarning = 'Y'
      OverdueWarning = "進項稅即將過期 (剩<天數>天)"

    IF DaysUntilExpiry <= 0:
      IsExpiringWarning = 'Y'
      OverdueWarning = "進項稅已過期 (<過期天數>天前)"
      → 系統鎖定該發票，不可再扣抵

系統操作:
1. 在 TW_TaxStatement 級別追蹤:
   ExpiringInvoiceCount = COUNT(*) WHERE DaysUntilExpiry <= 30
   ExpiredInvoiceCount = COUNT(*) WHERE DaysUntilExpiry <= 0

2. 每日/每週掃描:
   SELECT * FROM TW_Invoice_Prefix_Map
   WHERE IsExpiringWarning = 'Y'
   ORDER BY ExpiryDate

3. 自動警告與提示

例:
  2016年開立的進項發票 → 2026年3月24日過期
  若今天是2026-02-24，DaysUntilExpiry = 28天 → 警告提示
  若今天是2026-03-25，DaysUntilExpiry = -1天 → 已過期，鎖定
```

---

## III. 系統實現流程

### 銷售發票流程（三聯式/二聯式自動選擇）

```
SO (銷售訂單)
  ↓
出貨 (Shipment) / 完成出貨
  ↓
銷售發票 (C_Invoice)
  ├─ 自動選擇字軌
  │  └─ 檢查客戶是否有統編 → YES: SALES_TRIPART, NO: SALES_BIPART
  │
  ├─ 自動分配號碼
  │  ├─ SELECT TW_InvoicePrefix WHERE Status='A' AND InvoiceType='SALES_TRIPART'
  │  ├─ CurrentNumber++
  │  ├─ 檢查 CurrentNumber <= EndNumber
  │  └─ IF 超過: 警告 "字軌號碼已用盡，請停用並建立新字軌"
  │
  ├─ 驗證發票日期
  │  ├─ DateInvoiced >= 今天 OR DateInvoiced <= 今天? (通常是今天)
  │  ├─ DateInvoiced >= PreviousInvoiceDateInPrefix? (日期遞增)
  │  ├─ IsDateSequenceValid = 'Y' OR 'N'
  │  └─ IF 'N': 警告但允許保存（註記異常）
  │
  ├─ 決定申報期間
  │  └─ ActualReportingPeriod = MAP(DateInvoiced → Period 1-6)
  │
  ├─ 設定金額計算方式
  │  ├─ IF 客戶有統編: IsUnitPriceIncludeTax = 'N' (三聯式)
  │  ├─ IF 客戶無統編: IsUnitPriceIncludeTax = 'Y' (二聯式, 自動拆分)
  │  └─ 自動生成 QR Code (發票號+日期+稅額等)
  │
  ├─ 建立追蹤記錄
  │  ├─ INSERT INTO TW_Invoice_Prefix_Map
  │  │  ├─ InvoiceNumber, InvoiceType, DateInvoiced
  │  │  ├─ IsDateSequenceValid, PreviousInvoiceDateInPrefix
  │  │  ├─ ExpiryDate = DateInvoiced + 10 years
  │  │  └─ IsMonthConsistent = CHECK(InvoiceMonth = TransactionMonth)
  │  └─ UPDATE TW_InvoicePrefix
  │     ├─ CurrentNumber++
  │     └─ LastIssuedInvoiceDate = DateInvoiced
  │
  ├─ 更新稅務申報表
  │  ├─ UPDATE TW_TaxStatement (Period X)
  │  ├─ TaxableSalesAmount += 銷售額
  │  ├─ OutputTax += (銷售額 × 5%)
  │  └─ IF 免稅商品: TaxFreeSalesAmount += 銷售額
  │
  └─ 完成 ✓
      └─ 自動生成稅務單據
```

### 進項發票與折讓流程

```
PO (採購訂單)
  ↓
收貨 (Goods Receipt)
  ↓
進項發票 (C_Invoice, Incoming)
  ├─ 手動輸入發票號（掃描或手輸）
  │
  ├─ 驗證與分類
  │  ├─ 檢查發票號格式 (AA2600001)
  │  ├─ 檢查是否已輸入過 (重複檢查)
  │  ├─ 分類稅務屬性
  │  │  ├─ TW_TaxDeductible = Y/N (可否扣抵)
  │  │  └─ TW_TaxCategory = BUSINESS/ENTERTAINMENT/MEAL/...
  │  └─ 檢查日期
  │     ├─ IsDateSequenceValid? (同字軌日期遞增)
  │     └─ DaysUntilExpiry = ExpiryDate - TODAY()
  │
  ├─ 決定申報期間
  │  └─ ActualReportingPeriod = MAP(DateInvoiced → Period 1-6)
  │
  ├─ 更新稅務申報表
  │  ├─ UPDATE TW_TaxStatement (Period X)
  │  ├─ IF TW_TaxDeductible='Y': DeductibleInputTax += 稅額
  │  ├─ IF TW_TaxDeductible='N': NonDeductibleInputTax += 稅額
  │  └─ 更新 MixedTaxableMode (if applicable)
  │
  └─ 保存

---

發票折讓 (Invoice Adjustment/Credit Note)
  ├─ 建立折讓單據
  │  ├─ C_Invoice (Adjustment Document)
  │  └─ Link: 原始進項發票 (FK)
  │
  ├─ 計算折讓金額
  │  ├─ AdjustmentAmount = 折讓銷售額
  │  ├─ AdjustmentTax = 折讓稅額 (AdjustmentAmount × 5%)
  │  └─ 驗證: AdjustmentAmount <= 原始發票金額
  │
  ├─ ⭐ 強制當期申報
  │  ├─ RequiredReportingPeriod = 原始發票的 DateInvoiced 對應期間
  │  ├─ 開立折讓日期 (AdjustmentDate) 獨立計算
  │  │  └─ 但申報期間由原始發票決定
  │  └─ IF AdjustmentDate NOT IN RequiredReportingPeriod:
  │     → 警告: "請在 Period X 內申報此折讓"
  │     → IsReportedOnTime = 'N' IF 超期申報
  │
  ├─ 更新稅務申報表
  │  ├─ UPDATE TW_TaxStatement (Period X, RequiredReportingPeriod)
  │  ├─ DeductibleInputTax -= AdjustmentTax
  │  ├─ AdjustedInputTax += AdjustmentTax
  │  ├─ IF 超期申報: 自動扣減該期進項稅（國稅局規定）
  │  └─ AdjustmentCount++
  │
  └─ 完成 ✓
```

---

## IV. 驗證檢查清單

### A. 發票開立驗證
- [ ] DateInvoiced <= TODAY() (不能未來開立)
- [ ] IsDateSequenceValid (同字軌日期遞增)
- [ ] IsMonthConsistent (發票月份與交易月份一致)
- [ ] ActualReportingPeriod 正確對應

### B. 金額計算驗證
- [ ] 三聯式: 銷售額 × 1.05 = 含稅金額
- [ ] 二聯式: 含稅金額 ÷ 1.05 = 銷售額
- [ ] IsUnitPriceIncludeTax 與發票類型一致

### C. 進項稅折讓驗證
- [ ] RequiredReportingPeriod 正確計算
- [ ] IsReportedOnTime 正確標記
- [ ] OverdueAdjustmentCount 統計正確

### D. 兼營營業人驗證
- [ ] HasMinimum9MonthsOfMixedBusiness 判定正確
- [ ] ActualTaxableRatio 計算正確
- [ ] RatioAdjustedInputTax 正確調整
- [ ] DeferAdjustmentToNextYear 的邏輯正確

### E. 期限管理驗證
- [ ] DaysUntilExpiry 計算正確 (10年期限)
- [ ] IsExpiringWarning 提前30天警告
- [ ] 過期發票自動鎖定

---

## V. 待網路驗證的關鍵項目

1. **發票日期順序規定** - 是否必須遞增？
2. **兼營營業人9個月規則** - 是否在法規中有明確規定？
3. **"依第四章第二節規定"銷售額** - 具體定義？
4. **進項稅10年期限** - 確認計算方式？
5. **發票月份與交易月份** - 是否必須完全一致？

---

**下一步**: 通過網路搜索逐項驗證上述設計規則
