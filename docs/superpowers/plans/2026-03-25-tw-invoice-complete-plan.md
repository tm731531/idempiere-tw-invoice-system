# Taiwan Invoice Tax System — Complete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Agent assignments:** See `AGENTS.md` for full role descriptions and review checklists.
> **Project rules:** See `CLAUDE.md` for iDempiere patterns, domain rules, and what NOT to do.

**Goal:** 以 iDempiere OSGi Plugin 的形式，實現符合台灣統一發票與營業稅法規的完整系統，透過 2Pack ZIP 安裝字典/UI 定義，透過 Model 層實現業務邏輯。

**Architecture:** Plugin 啟動時 Activator 呼叫 PackIn 灌入 2Pack ZIP（建立 AD_Table/Window/UI），Model 層實作業務規則，Service 層處理發票編號、稅額計算、兼營調整。兩段互相獨立：2Pack 管資料字典，Java 程式管業務邏輯。

**Tech Stack:** iDempiere 12.0 OSGi Bundle, Java 17, PostgreSQL, Maven, JUnit 4

---

## 2Pack ↔ Model 對應規則（iDempiere 原始碼驗證）

| 2Pack XML 元素 | Java Model 要求 | 嚴格程度 |
|----------------|-----------------|---------|
| `<TableName>TW_InvoicePrefix</TableName>` | `Table_Name = "TW_InvoicePrefix"` | 必須完全一致 |
| `<ColumnName>PrefixCode</ColumnName>` | `COLUMNNAME_PrefixCode = "PrefixCode"` | 必須一致 |
| `<AccessLevel>3</AccessLevel>` | `get_AccessLevel() { return 3; }` | 習慣上一致 |
| `<AD_Reference_ID>13</AD_Reference_ID>`（PK） | constructor 接受 `int ID` | Integer 型別必要 |
| `<IsKey>true</IsKey>` | 必有 `(ctx, int ID, trxName)` constructor | PO.get_ID() 必要 |
| 2Pack 安裝後的 `AD_Table_ID` | `Table_ID = 0`（動態，不可寫死） | 執行期查詢 |

---

## 現況盤點

| 元件 | 狀態 | 備注 |
|------|------|------|
| OSGi Bundle 骨架 | ✅ | pom.xml, MANIFEST.MF, OSGI-INF/component.xml |
| TaiwanInvoiceTaxActivator | ⚠️ | 存在但缺 PackIn 邏輯 |
| MInvoicePrefix | ❌ 需修正 | 缺 `@Model`；`initPO()` 回傳 null 會崩潰 |
| MInvoicePrefixMap | ❌ 需修正 | 同上 |
| MInvoiceAdjustment | ❌ 需修正 | 同上 |
| MTaxStatement | ❌ 需修正 | 同上 |
| TaiwanModelFactory | ❌ 未建立 | **必須新建**，否則所有 Model 邏輯無效 |
| AccountingCodeMapper | ✅ | |
| SQL Scripts (5個) | ✅ | 2Pack 安裝後輔助使用 |
| 2Pack XML（4個） | ❌ 格式錯誤 | 需重寫為單一 PackOut.xml + ZIP |
| Phase 2 Service 層 | ❌ 未開始 | |
| Phase 3 Validator/Callout | ❌ 未開始 | |
| Phase 4 Process | ❌ 未開始 | |

---

## Phase 0.1：建立正確的 2Pack ZIP `[pack-builder]`

**Files:**
- Delete: `2pack/TW_InvoicePrefix.xml`, `TW_Invoice_Prefix_Map.xml`, `TW_InvoiceAdjustment.xml`, `TW_TaxStatement.xml`
- Create: `resources/2pack/tw_invoice_system/dict/PackOut.xml`
- Create: `resources/2pack/tw_invoice_system.zip`

### Step 0.1.1：生成穩定 UUID `[pack-builder]`

```bash
python3 -c "import uuid; [print(uuid.uuid4()) for _ in range(100)]" > /tmp/uuids.txt
cat /tmp/uuids.txt
```

記下並分配每個 UUID 給對應的字典物件（AD_EntityType, 4× AD_Reference, 4× AD_Table, ~40× AD_Column, 4× AD_Window, 4× AD_Tab, ~40× AD_Field, 4× AD_Menu）。

### Step 0.1.2：確認欄位名稱（AD_Org_ID vs C_Org_ID） `[pack-builder]`

```bash
grep -rn "Org_ID" src/tw/idempiere/invoice/tax/model/*.java
grep -rn "Org_ID" resources/sql/*.sql 2>/dev/null
```

預期：所有地方都用 `AD_Org_ID`（iDempiere 標準）。如有不一致，以 Model 的 `COLUMNNAME_*` 為準。

### Step 0.1.3：建立目錄結構 `[pack-builder]`

```bash
mkdir -p /home/tom/idempiere-tw-invoice-system/resources/2pack/tw_invoice_system/dict
```

### Step 0.1.4：撰寫 PackOut.xml `[pack-builder]`

建立 `resources/2pack/tw_invoice_system/dict/PackOut.xml`，完整內容如下：

**Section 1：根元素 + AD_EntityType**
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
    <AD_EntityType_UU>UUID-FROM-STEP-0.1.1</AD_EntityType_UU>
  </AD_EntityType>
```

**Section 2：AD_Reference（4個 List types）**

```xml
  <!-- Reference: TW_InvoiceType -->
  <AD_Reference>
    <Name>TW_InvoiceType</Name>
    <ValidationType>L</ValidationType>
    <IsActive>Y</IsActive>
    <EntityType>TW</EntityType>
    <AD_Reference_UU>UUID-TW_INVOICETYPE</AD_Reference_UU>
    <AD_Ref_List>
      <Value>SALES_TRIPART</Value>
      <Name>三聯式</Name>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Ref_List_UU>UUID-TRIPART</AD_Ref_List_UU>
    </AD_Ref_List>
    <AD_Ref_List>
      <Value>SALES_BIPART</Value>
      <Name>二聯式</Name>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Ref_List_UU>UUID-BIPART</AD_Ref_List_UU>
    </AD_Ref_List>
  </AD_Reference>

  <!-- Reference: TW_InvoicePrefixStatus -->
  <AD_Reference>
    <Name>TW_InvoicePrefixStatus</Name>
    <ValidationType>L</ValidationType>
    <IsActive>Y</IsActive>
    <EntityType>TW</EntityType>
    <AD_Reference_UU>UUID-TW_PREFIXSTATUS</AD_Reference_UU>
    <AD_Ref_List>
      <Value>A</Value><Name>Active</Name><IsActive>Y</IsActive>
      <EntityType>TW</EntityType><AD_Ref_List_UU>UUID-STATUS-A</AD_Ref_List_UU>
    </AD_Ref_List>
    <AD_Ref_List>
      <Value>I</Value><Name>Inactive</Name><IsActive>Y</IsActive>
      <EntityType>TW</EntityType><AD_Ref_List_UU>UUID-STATUS-I</AD_Ref_List_UU>
    </AD_Ref_List>
    <AD_Ref_List>
      <Value>C</Value><Name>Complete</Name><IsActive>Y</IsActive>
      <EntityType>TW</EntityType><AD_Ref_List_UU>UUID-STATUS-C</AD_Ref_List_UU>
    </AD_Ref_List>
  </AD_Reference>

  <!-- Reference: TW_AdjustmentType -->
  <AD_Reference>
    <Name>TW_AdjustmentType</Name>
    <ValidationType>L</ValidationType>
    <IsActive>Y</IsActive>
    <EntityType>TW</EntityType>
    <AD_Reference_UU>UUID-TW_ADJTYPE</AD_Reference_UU>
    <AD_Ref_List>
      <Value>RETURN</Value><Name>退貨</Name><IsActive>Y</IsActive>
      <EntityType>TW</EntityType><AD_Ref_List_UU>UUID-ADJ-RETURN</AD_Ref_List_UU>
    </AD_Ref_List>
    <AD_Ref_List>
      <Value>ALLOWANCE</Value><Name>折讓</Name><IsActive>Y</IsActive>
      <EntityType>TW</EntityType><AD_Ref_List_UU>UUID-ADJ-ALLOWANCE</AD_Ref_List_UU>
    </AD_Ref_List>
  </AD_Reference>

  <!-- Reference: TW_TaxPeriod -->
  <AD_Reference>
    <Name>TW_TaxPeriod</Name>
    <ValidationType>L</ValidationType>
    <IsActive>Y</IsActive>
    <EntityType>TW</EntityType>
    <AD_Reference_UU>UUID-TW_TAXPERIOD</AD_Reference_UU>
    <!-- Values 1-6 for bimonthly periods -->
    <AD_Ref_List><Value>1</Value><Name>第1期(1-2月)</Name><IsActive>Y</IsActive><EntityType>TW</EntityType><AD_Ref_List_UU>UUID-PERIOD-1</AD_Ref_List_UU></AD_Ref_List>
    <AD_Ref_List><Value>2</Value><Name>第2期(3-4月)</Name><IsActive>Y</IsActive><EntityType>TW</EntityType><AD_Ref_List_UU>UUID-PERIOD-2</AD_Ref_List_UU></AD_Ref_List>
    <AD_Ref_List><Value>3</Value><Name>第3期(5-6月)</Name><IsActive>Y</IsActive><EntityType>TW</EntityType><AD_Ref_List_UU>UUID-PERIOD-3</AD_Ref_List_UU></AD_Ref_List>
    <AD_Ref_List><Value>4</Value><Name>第4期(7-8月)</Name><IsActive>Y</IsActive><EntityType>TW</EntityType><AD_Ref_List_UU>UUID-PERIOD-4</AD_Ref_List_UU></AD_Ref_List>
    <AD_Ref_List><Value>5</Value><Name>第5期(9-10月)</Name><IsActive>Y</IsActive><EntityType>TW</EntityType><AD_Ref_List_UU>UUID-PERIOD-5</AD_Ref_List_UU></AD_Ref_List>
    <AD_Ref_List><Value>6</Value><Name>第6期(11-12月)</Name><IsActive>Y</IsActive><EntityType>TW</EntityType><AD_Ref_List_UU>UUID-PERIOD-6</AD_Ref_List_UU></AD_Ref_List>
  </AD_Reference>
```

**Section 3：AD_Table + AD_Column（4張表）**

每張表的標準欄位 pattern（`TW_InvoicePrefix` 示例）：

```xml
  <AD_Table>
    <TableName>TW_InvoicePrefix</TableName>
    <Name>TW Invoice Prefix</Name>
    <Description>台灣發票字軌管理</Description>
    <AccessLevel>3</AccessLevel>
    <IsActive>Y</IsActive>
    <EntityType>TW</EntityType>
    <AD_Table_UU>UUID-TABLE-INVOICEPREFIX</AD_Table_UU>

    <!-- Standard: AD_Client_ID -->
    <AD_Column>
      <ColumnName>AD_Client_ID</ColumnName>
      <Name>Client</Name>
      <AD_Reference_ID reference="id" reference-key="AD_Reference">19</AD_Reference_ID>
      <FieldLength>10</FieldLength>
      <IsMandatory>Y</IsMandatory>
      <IsKey>N</IsKey>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Column_UU>UUID-COL-PREFIX-CLIENT</AD_Column_UU>
    </AD_Column>

    <!-- Standard: AD_Org_ID -->
    <AD_Column>
      <ColumnName>AD_Org_ID</ColumnName>
      <Name>Organization</Name>
      <AD_Reference_ID reference="id" reference-key="AD_Reference">19</AD_Reference_ID>
      <FieldLength>10</FieldLength>
      <IsMandatory>Y</IsMandatory>
      <IsKey>N</IsKey>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Column_UU>UUID-COL-PREFIX-ORG</AD_Column_UU>
    </AD_Column>

    <!-- PK -->
    <AD_Column>
      <ColumnName>TW_InvoicePrefix_ID</ColumnName>
      <Name>TW Invoice Prefix</Name>
      <AD_Reference_ID reference="id" reference-key="AD_Reference">13</AD_Reference_ID>
      <FieldLength>10</FieldLength>
      <IsMandatory>Y</IsMandatory>
      <IsKey>Y</IsKey>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Column_UU>UUID-COL-PREFIX-ID</AD_Column_UU>
    </AD_Column>

    <!-- UUID -->
    <AD_Column>
      <ColumnName>TW_InvoicePrefix_UU</ColumnName>
      <Name>TW_InvoicePrefix_UU</Name>
      <AD_Reference_ID reference="id" reference-key="AD_Reference">10</AD_Reference_ID>
      <FieldLength>36</FieldLength>
      <IsMandatory>N</IsMandatory>
      <IsKey>N</IsKey>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Column_UU>UUID-COL-PREFIX-UU</AD_Column_UU>
    </AD_Column>

    <!-- Business columns -->
    <AD_Column>
      <ColumnName>PrefixCode</ColumnName>
      <Name>字軌代號</Name>
      <AD_Reference_ID reference="id" reference-key="AD_Reference">10</AD_Reference_ID>
      <FieldLength>2</FieldLength>
      <IsMandatory>Y</IsMandatory>
      <IsKey>N</IsKey>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Column_UU>UUID-COL-PREFIXCODE</AD_Column_UU>
    </AD_Column>

    <AD_Column>
      <ColumnName>InvoiceType</ColumnName>
      <Name>發票類型</Name>
      <AD_Reference_ID reference="id" reference-key="AD_Reference">17</AD_Reference_ID>
      <AD_Reference_Value_ID reference="uuid" reference-key="AD_Reference">UUID-TW_INVOICETYPE</AD_Reference_Value_ID>
      <FieldLength>20</FieldLength>
      <IsMandatory>Y</IsMandatory>
      <IsKey>N</IsKey>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Column_UU>UUID-COL-INVOICETYPE</AD_Column_UU>
    </AD_Column>

    <AD_Column>
      <ColumnName>StartNumber</ColumnName>
      <Name>起始號碼</Name>
      <AD_Reference_ID reference="id" reference-key="AD_Reference">11</AD_Reference_ID>
      <FieldLength>10</FieldLength>
      <IsMandatory>Y</IsMandatory>
      <IsKey>N</IsKey>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Column_UU>UUID-COL-STARTNUMBER</AD_Column_UU>
    </AD_Column>

    <AD_Column>
      <ColumnName>EndNumber</ColumnName>
      <Name>結束號碼</Name>
      <AD_Reference_ID reference="id" reference-key="AD_Reference">11</AD_Reference_ID>
      <FieldLength>10</FieldLength>
      <IsMandatory>Y</IsMandatory>
      <IsKey>N</IsKey>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Column_UU>UUID-COL-ENDNUMBER</AD_Column_UU>
    </AD_Column>

    <AD_Column>
      <ColumnName>CurrentNumber</ColumnName>
      <Name>目前號碼</Name>
      <AD_Reference_ID reference="id" reference-key="AD_Reference">11</AD_Reference_ID>
      <FieldLength>10</FieldLength>
      <IsMandatory>Y</IsMandatory>
      <IsKey>N</IsKey>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Column_UU>UUID-COL-CURRENTNUMBER</AD_Column_UU>
    </AD_Column>

    <AD_Column>
      <ColumnName>Status</ColumnName>
      <Name>狀態</Name>
      <AD_Reference_ID reference="id" reference-key="AD_Reference">17</AD_Reference_ID>
      <AD_Reference_Value_ID reference="uuid" reference-key="AD_Reference">UUID-TW_PREFIXSTATUS</AD_Reference_Value_ID>
      <FieldLength>1</FieldLength>
      <IsMandatory>Y</IsMandatory>
      <IsKey>N</IsKey>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Column_UU>UUID-COL-STATUS</AD_Column_UU>
    </AD_Column>

    <AD_Column>
      <ColumnName>LastIssuedInvoiceDate</ColumnName>
      <Name>最後開立日期</Name>
      <AD_Reference_ID reference="id" reference-key="AD_Reference">15</AD_Reference_ID>
      <FieldLength>7</FieldLength>
      <IsMandatory>N</IsMandatory>
      <IsKey>N</IsKey>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Column_UU>UUID-COL-LASTDATE</AD_Column_UU>
    </AD_Column>

    <AD_Column>
      <ColumnName>LastInvoiceNumber</ColumnName>
      <Name>最後發票號碼</Name>
      <AD_Reference_ID reference="id" reference-key="AD_Reference">10</AD_Reference_ID>
      <FieldLength>10</FieldLength>
      <IsMandatory>N</IsMandatory>
      <IsKey>N</IsKey>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Column_UU>UUID-COL-LASTINVOICE</AD_Column_UU>
    </AD_Column>

    <!-- Standard audit columns: IsActive, Created, CreatedBy, Updated, UpdatedBy -->
    <!-- ... follow standard iDempiere pattern ... -->
  </AD_Table>

  <!-- TW_Invoice_Prefix_Map, TW_InvoiceAdjustment, TW_TaxStatement: follow same pattern -->
```

**Section 4：AD_Window / AD_Tab / AD_Field + AD_Menu（4組）**

```xml
  <AD_Window>
    <Name>TW Invoice Prefix</Name>
    <Description>台灣發票字軌管理</Description>
    <WindowType>M</WindowType>
    <IsActive>Y</IsActive>
    <EntityType>TW</EntityType>
    <AD_Window_UU>UUID-WINDOW-PREFIX</AD_Window_UU>

    <AD_Tab>
      <Name>TW Invoice Prefix</Name>
      <AD_Table_UU>UUID-TABLE-INVOICEPREFIX</AD_Table_UU>
      <SeqNo>10</SeqNo>
      <TabLevel>0</TabLevel>
      <IsActive>Y</IsActive>
      <EntityType>TW</EntityType>
      <AD_Tab_UU>UUID-TAB-PREFIX</AD_Tab_UU>

      <AD_Field><Name>字軌代號</Name><AD_Column_UU>UUID-COL-PREFIXCODE</AD_Column_UU><SeqNo>10</SeqNo><IsActive>Y</IsActive><EntityType>TW</EntityType><AD_Field_UU>UUID-FIELD-PREFIXCODE</AD_Field_UU></AD_Field>
      <AD_Field><Name>發票類型</Name><AD_Column_UU>UUID-COL-INVOICETYPE</AD_Column_UU><SeqNo>20</SeqNo><IsActive>Y</IsActive><EntityType>TW</EntityType><AD_Field_UU>UUID-FIELD-INVOICETYPE</AD_Field_UU></AD_Field>
      <AD_Field><Name>起始號碼</Name><AD_Column_UU>UUID-COL-STARTNUMBER</AD_Column_UU><SeqNo>30</SeqNo><IsActive>Y</IsActive><EntityType>TW</EntityType><AD_Field_UU>UUID-FIELD-START</AD_Field_UU></AD_Field>
      <AD_Field><Name>結束號碼</Name><AD_Column_UU>UUID-COL-ENDNUMBER</AD_Column_UU><SeqNo>40</SeqNo><IsActive>Y</IsActive><EntityType>TW</EntityType><AD_Field_UU>UUID-FIELD-END</AD_Field_UU></AD_Field>
      <AD_Field><Name>目前號碼</Name><AD_Column_UU>UUID-COL-CURRENTNUMBER</AD_Column_UU><SeqNo>50</SeqNo><IsActive>Y</IsActive><EntityType>TW</EntityType><AD_Field_UU>UUID-FIELD-CURRENT</AD_Field_UU></AD_Field>
      <AD_Field><Name>狀態</Name><AD_Column_UU>UUID-COL-STATUS</AD_Column_UU><SeqNo>60</SeqNo><IsActive>Y</IsActive><EntityType>TW</EntityType><AD_Field_UU>UUID-FIELD-STATUS</AD_Field_UU></AD_Field>
      <AD_Field><Name>最後開立日期</Name><AD_Column_UU>UUID-COL-LASTDATE</AD_Column_UU><SeqNo>70</SeqNo><IsActive>Y</IsActive><EntityType>TW</EntityType><AD_Field_UU>UUID-FIELD-LASTDATE</AD_Field_UU></AD_Field>
    </AD_Tab>
  </AD_Window>

  <AD_Menu>
    <Name>TW Invoice Prefix</Name>
    <Description>台灣發票字軌管理</Description>
    <IsActive>Y</IsActive>
    <IsSummary>N</IsSummary>
    <Action>W</Action>
    <AD_Window_UU>UUID-WINDOW-PREFIX</AD_Window_UU>
    <EntityType>TW</EntityType>
    <AD_Menu_UU>UUID-MENU-PREFIX</AD_Menu_UU>
  </AD_Menu>

  <!-- Repeat for TW_Invoice_Prefix_Map, TW_InvoiceAdjustment, TW_TaxStatement -->

</idempiere>
```

### Step 0.1.5：驗證 XML `[pack-builder]`

```bash
xmllint --noout resources/2pack/tw_invoice_system/dict/PackOut.xml
echo "XML validation exit code: $?"
```

Expected: exit code 0

### Step 0.1.6：打包成 ZIP `[pack-builder]`

```bash
cd /home/tom/idempiere-tw-invoice-system/resources/2pack
zip -r tw_invoice_system.zip tw_invoice_system/
unzip -l tw_invoice_system.zip
```

Expected output contains: `tw_invoice_system/dict/PackOut.xml`

### Step 0.1.7：刪除舊的錯誤格式 XML `[pack-builder]`

```bash
git rm 2pack/TW_InvoicePrefix.xml 2pack/TW_Invoice_Prefix_Map.xml \
       2pack/TW_InvoiceAdjustment.xml 2pack/TW_TaxStatement.xml
```

### Step 0.1.8：Commit `[pack-builder]`

```bash
git add resources/2pack/
git commit -m "feat: add proper iDempiere 2Pack ZIP with PackOut.xml (replaces old XML files)"
```

---

### ★ Review Gate 0.1 — `[idempiere-expert]` + `[tw-invoice-pm]`

Use review checklists in `AGENTS.md` sections for `idempiere-expert` (2Pack / PackOut.xml) and `tw-invoice-pm` (data model completeness, Chinese field labels).

**Blocking issues must be fixed before Phase 0.2/0.3 starts.**

---

## Phase 0.2：更新 Activator（PackIn 邏輯） `[activator-fixer]`

**Depends on:** Phase 0.1 complete (ZIP must exist at `resources/2pack/tw_invoice_system.zip`)

**Files:**
- Modify: `src/tw/idempiere/invoice/tax/TaiwanInvoiceTaxActivator.java`
- Modify: `META-INF/MANIFEST.MF`
- Create: `test/tw/idempiere/invoice/tax/ActivatorPackInTest.java`

### Step 0.2.1：寫測試 `[activator-fixer]`

Create `test/tw/idempiere/invoice/tax/ActivatorPackInTest.java`:

```java
package tw.idempiere.invoice.tax;

import org.junit.Test;
import static org.junit.Assert.*;

public class ActivatorPackInTest {
    @Test
    public void testPackInResourceExists() {
        java.net.URL zipUrl = TaiwanInvoiceTaxActivator.class
            .getResource("/2pack/tw_invoice_system.zip");
        assertNotNull("2Pack ZIP must exist at /2pack/tw_invoice_system.zip", zipUrl);
    }
}
```

### Step 0.2.2：確認測試失敗 `[activator-fixer]`

```bash
mvn test -Dtest=ActivatorPackInTest
```

Expected: FAIL (ZIP not yet on classpath)

### Step 0.2.3：更新 pom.xml — 加入 resources 路徑 `[activator-fixer]`

In `pom.xml`, ensure `resources/2pack` is on the classpath:

```xml
<build>
  <resources>
    <resource>
      <directory>resources</directory>
    </resource>
  </resources>
</build>
```

### Step 0.2.4：實作 `installDictionary()` `[activator-fixer]`

Modify `TaiwanInvoiceTaxActivator.java`:

```java
import java.io.*;
import java.net.URL;
import java.util.logging.Level;
import org.adempiere.pipo2.PackIn;
import org.compiere.util.Env;

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
            log.warning("2Pack ZIP not found — skipping dictionary install");
            return;
        }
        File tempZip = File.createTempFile("tw_invoice_system", ".zip");
        try (InputStream in = zipUrl.openStream();
             FileOutputStream out = new FileOutputStream(tempZip)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        PackIn packIn = new PackIn();
        packIn.importXML(tempZip.getAbsolutePath(), Env.getCtx(), null);
        log.info("Taiwan Invoice Tax System 2Pack installed successfully.");
        tempZip.delete();
    } catch (Exception e) {
        log.log(Level.WARNING,
            "2Pack install failed (tables may already exist): " + e.getMessage());
        // Non-fatal: safe to continue on reinstall
    }
}
```

### Step 0.2.5：更新 MANIFEST.MF `[activator-fixer]`

Add to `Import-Package`:
```
Import-Package: org.adempiere.base,
 org.adempiere.pipo2;version="[12.0.0,13.0.0)",
 org.compiere.model,
 org.compiere.util,
 org.compiere.process,
 org.osgi.framework;version="1.10.0"
```

Add to bundle classpath (so ZIP is accessible via `getResource`):
```
Bundle-ClassPath: .,resources/
```

### Step 0.2.6：確認測試通過 `[activator-fixer]`

```bash
mvn test -Dtest=ActivatorPackInTest
```

Expected: PASS

### Step 0.2.7：編譯確認 `[activator-fixer]`

```bash
mvn compile
```

Expected: BUILD SUCCESS

### Step 0.2.8：Commit `[activator-fixer]`

```bash
git add src/tw/idempiere/invoice/tax/TaiwanInvoiceTaxActivator.java
git add META-INF/MANIFEST.MF pom.xml
git add test/tw/idempiere/invoice/tax/ActivatorPackInTest.java
git commit -m "feat: add PackIn invocation in Activator and update MANIFEST imports"
```

---

### ★ Review Gate 0.2 — `[idempiere-expert]`

Check `AGENTS.md` → `idempiere-expert` → Activator checklist.

---

## Phase 0.3：修正 Model 類別 `[model-fixer]`

**Can run in parallel with Phase 0.2** (independent files)

**Files:**
- Create: `src/tw/idempiere/invoice/tax/model/TaiwanModelFactory.java`
- Modify: `OSGI-INF/component.xml`
- Modify: `src/tw/idempiere/invoice/tax/model/MInvoicePrefix.java`
- Modify: `src/tw/idempiere/invoice/tax/model/MInvoicePrefixMap.java`
- Modify: `src/tw/idempiere/invoice/tax/model/MInvoiceAdjustment.java`
- Modify: `src/tw/idempiere/invoice/tax/model/MTaxStatement.java`

### Task 0.3.1：建立 TaiwanModelFactory `[model-fixer]`

**Step 0.3.1.1：建立 TaiwanModelFactory.java**

```java
package tw.idempiere.invoice.tax.model;

import org.adempiere.base.AnnotationBasedModelFactory;
import org.adempiere.base.IModelFactory;
import org.osgi.service.component.annotations.Component;

@Component(
    immediate = true,
    service = IModelFactory.class,
    property = {"service.ranking:Integer=10"}
)
public class TaiwanModelFactory extends AnnotationBasedModelFactory {
    @Override
    protected String[] getPackages() {
        return new String[]{"tw.idempiere.invoice.tax.model"};
    }
}
```

**Step 0.3.1.2：加入 OSGI-INF/component.xml 服務宣告**

Add to `OSGI-INF/component.xml` (alongside existing activator component):

```xml
<component name="tw.idempiere.invoice.tax.modelFactory"
           immediate="true">
    <implementation class="tw.idempiere.invoice.tax.model.TaiwanModelFactory"/>
    <service>
        <provide interface="org.adempiere.base.IModelFactory"/>
    </service>
</component>
```

**Step 0.3.1.3：確認 MANIFEST.MF Import 包含 org.adempiere.base**

```bash
grep "org.adempiere.base" META-INF/MANIFEST.MF
```

Expected: line present

### Task 0.3.2：修正 4 個 Model 類 `[model-fixer]`

Apply to **all 4 model classes** (`MInvoicePrefix`, `MInvoicePrefixMap`, `MInvoiceAdjustment`, `MTaxStatement`):

**Step 0.3.2.1：加入 @Model annotation**

```java
import org.adempiere.base.Model;

@Model(table = MInvoicePrefix.Table_Name)   // adjust class name per file
public class MInvoicePrefix extends PO {
    public static final String Table_Name = "TW_InvoicePrefix";
    public static int Table_ID = 0;  // dynamic, populated after 2Pack installs
    ...
```

**Step 0.3.2.2：修正 initPO() — 不可回傳 null**

```java
@Override
protected POInfo initPO(Properties ctx) {
    // Uses Table_Name lookup — works after 2Pack installs AD_Table entry
    return POInfo.getPOInfo(ctx, Table_Name);
}
```

**Step 0.3.2.3：確認 get_AccessLevel() 回傳 3**

```java
@Override
protected int get_AccessLevel() {
    return 3;  // Client + Org — matches <AccessLevel>3</AccessLevel> in PackOut.xml
}
```

**Step 0.3.2.4：寫 @Model annotation 測試**

Create/update `test/tw/idempiere/invoice/tax/model/ModelAnnotationTest.java`:

```java
package tw.idempiere.invoice.tax.model;

import org.adempiere.base.Model;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModelAnnotationTest {
    @Test
    public void testMInvoicePrefix_hasModelAnnotation() {
        Model annotation = MInvoicePrefix.class.getAnnotation(Model.class);
        assertNotNull("@Model annotation must be present on MInvoicePrefix", annotation);
        assertEquals("TW_InvoicePrefix", annotation.table());
    }
    @Test
    public void testMInvoicePrefixMap_hasModelAnnotation() {
        Model annotation = MInvoicePrefixMap.class.getAnnotation(Model.class);
        assertNotNull(annotation);
        assertEquals("TW_Invoice_Prefix_Map", annotation.table());
    }
    @Test
    public void testMInvoiceAdjustment_hasModelAnnotation() {
        Model annotation = MInvoiceAdjustment.class.getAnnotation(Model.class);
        assertNotNull(annotation);
        assertEquals("TW_InvoiceAdjustment", annotation.table());
    }
    @Test
    public void testMTaxStatement_hasModelAnnotation() {
        Model annotation = MTaxStatement.class.getAnnotation(Model.class);
        assertNotNull(annotation);
        assertEquals("TW_TaxStatement", annotation.table());
    }
}
```

**Step 0.3.2.5：確認測試通過**

```bash
mvn test -Dtest=ModelAnnotationTest
```

Expected: 4 tests PASS

**Step 0.3.2.6：編譯確認**

```bash
mvn compile
```

Expected: BUILD SUCCESS

**Step 0.3.2.7：Commit**

```bash
git add src/tw/idempiere/invoice/tax/model/
git add OSGI-INF/component.xml
git add test/tw/idempiere/invoice/tax/model/ModelAnnotationTest.java
git commit -m "fix: add @Model annotation, TaiwanModelFactory OSGi service, fix initPO() null crash"
```

---

### ★ Review Gate 0.3 — `[idempiere-expert]`

Check `AGENTS.md` → `idempiere-expert` → Model classes + TaiwanModelFactory checklists.

---

### ★ Phase 1 Column Alignment Check — `[idempiere-expert]`

After Gate 0.3, verify existing Phase 1 code aligns with 2Pack:

```bash
# Extract ColumnNames from PackOut.xml
grep "<ColumnName>" resources/2pack/tw_invoice_system/dict/PackOut.xml

# Extract COLUMNNAME_ constants from Model classes
grep "COLUMNNAME_" src/tw/idempiere/invoice/tax/model/*.java

# Extract column names from SQL scripts
grep -i "column\|field" resources/sql/*.sql 2>/dev/null
```

- [ ] Every COLUMNNAME_* constant has a matching `<ColumnName>` in PackOut.xml
- [ ] SQL scripts use same column names as Model constants
- [ ] If mismatches found: update PackOut.xml to match Java constants (not the reverse)

---

## Phase 2：業務邏輯服務層 `[service-builder]`

**Depends on:** Gate 0.3 approved

All services are **pure Java** — no iDempiere runtime required. Fully unit-testable.

### Task 2.1：InvoiceNumberingService `[service-builder]`

**Files:**
- Create: `src/tw/idempiere/invoice/tax/service/InvoiceNumberingService.java`
- Create: `test/tw/idempiere/invoice/tax/service/InvoiceNumberingServiceTest.java`

**Step 2.1.1：寫失敗測試**

```java
package tw.idempiere.invoice.tax.service;

import org.junit.Test;
import static org.junit.Assert.*;

public class InvoiceNumberingServiceTest {
    @Test
    public void testFormatInvoiceNumber_tripart() {
        // AA prefix + number 1 → "AA00000001"
        String result = InvoiceNumberingService.formatInvoiceNumber("AA", 1);
        assertEquals("AA00000001", result);
    }

    @Test
    public void testFormatInvoiceNumber_paddingEight() {
        String result = InvoiceNumberingService.formatInvoiceNumber("ZZ", 99999999);
        assertEquals("ZZ99999999", result);
    }

    @Test
    public void testIsExhausted_whenCurrentExceedsEnd() {
        assertTrue(InvoiceNumberingService.isExhausted(100, 99));
    }

    @Test
    public void testIsExhausted_whenCurrentEqualsEnd() {
        assertFalse(InvoiceNumberingService.isExhausted(99, 99));
    }

    @Test
    public void testIsExhausted_whenCurrentBelowEnd() {
        assertFalse(InvoiceNumberingService.isExhausted(1, 99));
    }
}
```

**Step 2.1.2：確認測試失敗**

```bash
mvn test -Dtest=InvoiceNumberingServiceTest
```

Expected: FAIL — class not found

**Step 2.1.3：實作 InvoiceNumberingService**

```java
package tw.idempiere.invoice.tax.service;

public class InvoiceNumberingService {
    public static String formatInvoiceNumber(String prefixCode, int number) {
        return prefixCode + String.format("%08d", number);
    }

    public static boolean isExhausted(int currentNumber, int endNumber) {
        return currentNumber > endNumber;
    }

    // DB-layer method signatures (implemented against Model layer):
    // public static String allocateNextNumber(Properties ctx, String invoiceType, String trxName)
    // — locks TW_InvoicePrefix FOR UPDATE, increments CurrentNumber, returns formatted number
    // — if prefix exhausted: sets Status=C, finds next Active prefix
}
```

**Step 2.1.4：確認測試通過**

```bash
mvn test -Dtest=InvoiceNumberingServiceTest
```

Expected: 5 tests PASS

---

### Task 2.2：TaxCalculationService `[service-builder]`

**Files:**
- Create: `src/tw/idempiere/invoice/tax/service/TaxCalculationService.java`
- Create: `test/tw/idempiere/invoice/tax/service/TaxCalculationServiceTest.java`

**Step 2.2.1：寫失敗測試**

```java
package tw.idempiere.invoice.tax.service;

import java.math.BigDecimal;
import java.time.Month;
import org.junit.Test;
import static org.junit.Assert.*;

public class TaxCalculationServiceTest {

    @Test
    public void testTripartOutputTax_floor() {
        // 100001 × 0.05 = 5000.05 → FLOOR → 5000 (not round to 5001)
        BigDecimal tax = TaxCalculationService.calcOutputTax(new BigDecimal("100001"));
        assertEquals(new BigDecimal("5000"), tax);
    }

    @Test
    public void testTripartGrossAmount() {
        // saleAmount × 1.05 = grossAmount
        BigDecimal gross = TaxCalculationService.calcGrossAmount(new BigDecimal("100000"));
        assertEquals(new BigDecimal("105000.00"), gross);
    }

    @Test
    public void testBipartReverseSaleAmount() {
        // floor(105000 / 1.05) = 100000
        TaxCalculationService.TaxResult r =
            TaxCalculationService.calcFromGross(new BigDecimal("105000"));
        assertEquals(new BigDecimal("100000"), r.getSaleAmount());
        assertEquals(new BigDecimal("5000"), r.getTaxAmount());
    }

    @Test
    public void testReportingPeriod_january() {
        assertEquals(1, TaxCalculationService.getReportingPeriod(Month.JANUARY));
    }

    @Test
    public void testReportingPeriod_february() {
        assertEquals(1, TaxCalculationService.getReportingPeriod(Month.FEBRUARY));
    }

    @Test
    public void testReportingPeriod_may() {
        assertEquals(3, TaxCalculationService.getReportingPeriod(Month.MAY));
    }

    @Test
    public void testReportingPeriod_december() {
        assertEquals(6, TaxCalculationService.getReportingPeriod(Month.DECEMBER));
    }

    @Test
    public void testNoBigDecimalRounding_usesFloor() {
        // Verify 0.005 rounds DOWN (floor), not up (half-up)
        BigDecimal tax = TaxCalculationService.calcOutputTax(new BigDecimal("100"));
        // 100 × 0.05 = 5.00 exactly — passes both floor and round
        assertEquals(new BigDecimal("5"), tax);
        // Edge case: 101 × 0.05 = 5.05 → floor → 5
        BigDecimal tax2 = TaxCalculationService.calcOutputTax(new BigDecimal("101"));
        assertEquals(new BigDecimal("5"), tax2);
    }
}
```

**Step 2.2.2：確認測試失敗**

```bash
mvn test -Dtest=TaxCalculationServiceTest
```

**Step 2.2.3：實作 TaxCalculationService**

```java
package tw.idempiere.invoice.tax.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;

public class TaxCalculationService {

    private static final BigDecimal VAT_RATE = new BigDecimal("0.05");
    private static final BigDecimal ONE_PLUS_VAT = new BigDecimal("1.05");

    public static BigDecimal calcOutputTax(BigDecimal saleAmount) {
        return saleAmount.multiply(VAT_RATE).setScale(0, RoundingMode.FLOOR);
    }

    public static BigDecimal calcGrossAmount(BigDecimal saleAmount) {
        return saleAmount.multiply(ONE_PLUS_VAT).setScale(2, RoundingMode.FLOOR);
    }

    public static TaxResult calcFromGross(BigDecimal grossAmount) {
        BigDecimal saleAmount = grossAmount.divide(ONE_PLUS_VAT, 0, RoundingMode.FLOOR);
        BigDecimal taxAmount = grossAmount.subtract(saleAmount);
        return new TaxResult(saleAmount, taxAmount);
    }

    public static int getReportingPeriod(Month month) {
        return (month.getValue() - 1) / 2 + 1;
    }

    public static class TaxResult {
        private final BigDecimal saleAmount;
        private final BigDecimal taxAmount;
        public TaxResult(BigDecimal saleAmount, BigDecimal taxAmount) {
            this.saleAmount = saleAmount;
            this.taxAmount = taxAmount;
        }
        public BigDecimal getSaleAmount() { return saleAmount; }
        public BigDecimal getTaxAmount() { return taxAmount; }
    }
}
```

**Step 2.2.4：確認測試通過**

```bash
mvn test -Dtest=TaxCalculationServiceTest
```

Expected: 8 tests PASS

---

### Task 2.3：MixedBusinessService `[service-builder]`

**Files:**
- Create: `src/tw/idempiere/invoice/tax/service/MixedBusinessService.java`
- Create: `test/tw/idempiere/invoice/tax/service/MixedBusinessServiceTest.java`

**Step 2.3.1：寫失敗測試**

```java
package tw.idempiere.invoice.tax.service;

import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.*;

public class MixedBusinessServiceTest {

    @Test
    public void testLessThan9Months_noAdjustmentNeeded() {
        assertFalse(MixedBusinessService.isEligibleForAdjustment(8));
    }

    @Test
    public void testExactly9Months_adjustmentNeeded() {
        assertTrue(MixedBusinessService.isEligibleForAdjustment(9));
    }

    @Test
    public void testMoreThan9Months_adjustmentNeeded() {
        assertTrue(MixedBusinessService.isEligibleForAdjustment(12));
    }

    @Test
    public void testTaxableRatio_80percent() {
        BigDecimal ratio = MixedBusinessService.calcTaxableRatio(
            new BigDecimal("800000"), new BigDecimal("1000000"));
        assertEquals(new BigDecimal("0.8000"), ratio);
    }

    @Test
    public void testTaxableRatio_fullTaxable() {
        BigDecimal ratio = MixedBusinessService.calcTaxableRatio(
            new BigDecimal("1000000"), new BigDecimal("1000000"));
        assertEquals(new BigDecimal("1.0000"), ratio);
    }

    @Test
    public void testAdjustInputTax() {
        BigDecimal adjusted = MixedBusinessService.adjustInputTax(
            new BigDecimal("100000"), new BigDecimal("0.8000"));
        assertEquals(new BigDecimal("80000.0000"), adjusted);
    }

    @Test
    public void testZeroTotalRevenue_returnsZeroRatio() {
        BigDecimal ratio = MixedBusinessService.calcTaxableRatio(
            BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO, ratio);
    }
}
```

**Step 2.3.2：確認測試失敗**

```bash
mvn test -Dtest=MixedBusinessServiceTest
```

**Step 2.3.3：實作 MixedBusinessService**

```java
package tw.idempiere.invoice.tax.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MixedBusinessService {

    public static boolean isEligibleForAdjustment(int operatingMonths) {
        return operatingMonths >= 9;
    }

    public static BigDecimal calcTaxableRatio(BigDecimal taxableRevenue, BigDecimal totalRevenue) {
        if (totalRevenue == null || totalRevenue.compareTo(BigDecimal.ZERO) == 0)
            return BigDecimal.ZERO;
        return taxableRevenue.divide(totalRevenue, 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal adjustInputTax(BigDecimal inputTax, BigDecimal taxableRatio) {
        return inputTax.multiply(taxableRatio);
    }
}
```

**Step 2.3.4：確認測試通過**

```bash
mvn test -Dtest=MixedBusinessServiceTest
```

Expected: 7 tests PASS

---

### Task 2.4：InvoiceValidationService `[service-builder]`

**Files:**
- Create: `src/tw/idempiere/invoice/tax/service/InvoiceValidationService.java`
- Create: `src/tw/idempiere/invoice/tax/service/ValidationResult.java`
- Create: `test/tw/idempiere/invoice/tax/service/InvoiceValidationServiceTest.java`

**Step 2.4.1：寫失敗測試**

```java
package tw.idempiere.invoice.tax.service;

import java.time.LocalDate;
import org.junit.Test;
import static org.junit.Assert.*;

public class InvoiceValidationServiceTest {

    @Test
    public void testFutureDateRejected() {
        ValidationResult r = InvoiceValidationService.validateInvoiceDate(
            LocalDate.now().plusDays(1));
        assertFalse("Future date must be invalid", r.isValid());
        assertNotNull(r.getErrorMessage());
    }

    @Test
    public void testTodayDateAccepted() {
        ValidationResult r = InvoiceValidationService.validateInvoiceDate(LocalDate.now());
        assertTrue(r.isValid());
    }

    @Test
    public void testPastDateAccepted() {
        ValidationResult r = InvoiceValidationService.validateInvoiceDate(
            LocalDate.now().minusDays(1));
        assertTrue(r.isValid());
    }

    @Test
    public void testNonSequentialDate_warningNotBlock() {
        // older date after newer → warning, but still valid
        ValidationResult r = InvoiceValidationService.validateDateSequence(
            LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 1));
        assertTrue("Non-sequential date should not block save", r.isValid());
        assertTrue("Non-sequential date should produce warning", r.hasWarning());
    }

    @Test
    public void testSequentialDate_noWarning() {
        ValidationResult r = InvoiceValidationService.validateDateSequence(
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));
        assertTrue(r.isValid());
        assertFalse(r.hasWarning());
    }

    @Test
    public void testMonthMismatch_warningNotBlock() {
        ValidationResult r = InvoiceValidationService.validateMonthConsistency(
            LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 15));
        assertTrue(r.isValid());
        assertTrue(r.hasWarning());
    }

    @Test
    public void testMonthMatch_noWarning() {
        ValidationResult r = InvoiceValidationService.validateMonthConsistency(
            LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 20));
        assertTrue(r.isValid());
        assertFalse(r.hasWarning());
    }
}
```

**Step 2.4.2：確認測試失敗**

```bash
mvn test -Dtest=InvoiceValidationServiceTest
```

**Step 2.4.3：實作 ValidationResult**

```java
package tw.idempiere.invoice.tax.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationResult {
    private final boolean valid;
    private final String errorMessage;
    private final List<String> warnings;

    private ValidationResult(boolean valid, String errorMessage, List<String> warnings) {
        this.valid = valid;
        this.errorMessage = errorMessage;
        this.warnings = Collections.unmodifiableList(warnings);
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, null, new ArrayList<>());
    }

    public static ValidationResult error(String message) {
        return new ValidationResult(false, message, new ArrayList<>());
    }

    public ValidationResult withWarning(String warning) {
        List<String> w = new ArrayList<>(this.warnings);
        w.add(warning);
        return new ValidationResult(this.valid, this.errorMessage, w);
    }

    public boolean isValid() { return valid; }
    public String getErrorMessage() { return errorMessage; }
    public List<String> getWarnings() { return warnings; }
    public boolean hasWarning() { return !warnings.isEmpty(); }
}
```

**Step 2.4.4：實作 InvoiceValidationService**

```java
package tw.idempiere.invoice.tax.service;

import java.time.LocalDate;

public class InvoiceValidationService {

    public static ValidationResult validateInvoiceDate(LocalDate invoiceDate) {
        if (invoiceDate.isAfter(LocalDate.now()))
            return ValidationResult.error("發票日期不可為未來日期");
        return ValidationResult.ok();
    }

    public static ValidationResult validateDateSequence(LocalDate lastDate, LocalDate newDate) {
        if (newDate.isBefore(lastDate))
            return ValidationResult.ok().withWarning("發票日期早於同字軌最後開立日期，請確認");
        return ValidationResult.ok();
    }

    public static ValidationResult validateMonthConsistency(LocalDate invoiceDate, LocalDate trxDate) {
        if (invoiceDate.getMonth() != trxDate.getMonth() ||
            invoiceDate.getYear() != trxDate.getYear())
            return ValidationResult.ok().withWarning("發票月份與交易月份不一致，請確認");
        return ValidationResult.ok();
    }
}
```

**Step 2.4.5：確認測試通過**

```bash
mvn test -Dtest=InvoiceValidationServiceTest
```

Expected: 7 tests PASS

**Step 2.4.6：Run all service tests**

```bash
mvn test -Dtest="InvoiceNumberingServiceTest,TaxCalculationServiceTest,MixedBusinessServiceTest,InvoiceValidationServiceTest"
```

Expected: all PASS

**Step 2.4.7：Commit Phase 2**

```bash
git add src/tw/idempiere/invoice/tax/service/
git add test/tw/idempiere/invoice/tax/service/
git commit -m "feat: add Phase 2 service layer - numbering, tax calculation, mixed business, validation"
```

---

### ★ Review Gate 2 — `[tw-invoice-pm]`

Check `AGENTS.md` → `tw-invoice-pm` → Business rule compliance checklist.
Focus: tax floor calculation, bimonthly periods, warn-not-block rules.

---

## Phase 3：Validator + Callout 層 `[integration-builder]`

**Depends on:** Gate 2 approved

**Files:**
- Create: `src/tw/idempiere/invoice/tax/callout/InvoicePrefixCallout.java`
- Create: `src/tw/idempiere/invoice/tax/callout/TaxStatementCallout.java`
- Modify: `OSGI-INF/component.xml`

### Task 3.1：InvoicePrefixValidator (@ModelEvent) `[integration-builder]`

```java
package tw.idempiere.invoice.tax.callout;

import org.adempiere.base.event.annotations.ModelEvent;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.PO;
import tw.idempiere.invoice.tax.model.MInvoicePrefix;

public class InvoicePrefixValidator {

    @ModelEvent(
        tableName = MInvoicePrefix.Table_Name,
        type = {ModelValidationEngine.TIMING_BEFORE_NEW,
                ModelValidationEngine.TIMING_BEFORE_CHANGE}
    )
    public String onBeforeSave(PO po, int timing) {
        MInvoicePrefix prefix = (MInvoicePrefix) po;

        // Rule 1: PrefixCode must be 2 uppercase letters
        String code = prefix.getPrefixCode();
        if (code == null || !code.matches("[A-Z]{2}"))
            return "字軌代號必須為2個大寫英文字母";

        // Rule 2: StartNumber < EndNumber
        if (prefix.getStartNumber() >= prefix.getEndNumber())
            return "起始號碼必須小於結束號碼";

        // Rule 3: Status C cannot revert to A
        if (timing == ModelValidationEngine.TIMING_BEFORE_CHANGE) {
            String oldStatus = (String) prefix.get_ValueOld(MInvoicePrefix.COLUMNNAME_Status);
            if (MInvoicePrefix.STATUS_Complete.equals(oldStatus) &&
                MInvoicePrefix.STATUS_Active.equals(prefix.getStatus()))
                return "已完成的字軌不可重新設為使用中";
        }

        return null;  // null = no error
    }
}
```

Write tests for each rule in `test/tw/idempiere/invoice/tax/callout/InvoicePrefixValidatorTest.java`.

### Task 3.2：InvoicePrefixCallout `[integration-builder]`

Listens on `C_Invoice.C_BPartner_ID` change — auto-selects invoice type (TRIPART if B2B, BIPART if B2C).

```java
package tw.idempiere.invoice.tax.callout;

import org.adempiere.base.annotation.Callout;
// ... auto-detect whether BPartner has TaxID → set InvoiceType
```

### Task 3.3：TaxStatementCallout `[integration-builder]`

Listens on `TW_TaxStatement.TaxYear`/`TaxPeriod` change — auto-aggregates output/input tax for the period.

### Step 3.4：Register in OSGI-INF/component.xml `[integration-builder]`

Add component entries for each validator/callout that requires OSGi registration.

### Step 3.5：Test Phase 3 `[integration-builder]`

```bash
mvn test -Dtest="InvoicePrefixValidatorTest,InvoicePrefixCalloutTest,TaxStatementCalloutTest"
```

### Step 3.6：Commit Phase 3 `[integration-builder]`

```bash
git commit -m "feat: add Phase 3 validators and callouts"
```

---

### ★ Review Gate 3 — `[idempiere-expert]` + `[tw-invoice-pm]`

Check `AGENTS.md` review checklists for both reviewers.

---

## Phase 4：Process 層 `[integration-builder]`

**Depends on:** Gate 3 approved

**Files:**
- Create: `src/tw/idempiere/invoice/tax/process/GenerateTaxStatementProcess.java`
- Create: `src/tw/idempiere/invoice/tax/process/ExportTaxReportProcess.java`

### Task 4.1：GenerateTaxStatementProcess `[integration-builder]`

```
Flow:
1. Accept: TaxYear (int), TaxPeriod (1-6)
2. Query TW_Invoice_Prefix_Map for invoices in period → OutputTax
3. Query TW_InvoiceAdjustment for adjustments in period → InputTax
4. If isMixedBusiness: apply MixedBusinessService.adjustInputTax()
5. TaxPayable = OutputTax - AdjustedInputTax
6. Create TW_TaxStatement record
7. Return statement ID
```

### Task 4.2：ExportTaxReportProcess `[integration-builder]`

```
Flow:
1. Accept: TaxYear, TaxPeriod, ExportFormat (CSV/XML)
2. Load TW_TaxStatement for period
3. Format per Ministry of Finance specification
4. Write to output file / return as download
```

### Step 4.3：Test Phase 4 `[integration-builder]`

```bash
mvn test -Dtest="GenerateTaxStatementProcessTest,ExportTaxReportProcessTest"
```

### Step 4.4：Commit Phase 4 `[integration-builder]`

```bash
git commit -m "feat: add Phase 4 tax statement generation and export processes"
```

---

### ★ Review Gate 4 — `[idempiere-expert]` + `[tw-invoice-pm]`

Final review. `tw-invoice-pm` verifies export format against Ministry of Finance spec.

---

## Quick Reference: Agent→Phase Mapping

| Phase | Agent | Key Output |
|-------|-------|-----------|
| 0.1 | `pack-builder` | `resources/2pack/tw_invoice_system.zip` |
| 0.2 | `activator-fixer` | Updated Activator + MANIFEST.MF |
| 0.3 | `model-fixer` | `TaiwanModelFactory` + fixed 4 Models |
| Review | `idempiere-expert` | Technical approval at each gate |
| Review | `tw-invoice-pm` | Domain approval at each gate |
| 2 | `service-builder` | 4 pure-Java services with tests |
| 3–4 | `integration-builder` | Validators, callouts, processes |
