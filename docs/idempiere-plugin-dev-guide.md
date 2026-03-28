# iDempiere Plugin 開發實戰指南

> 從本專案（台灣統一發票系統）的開發與除錯經驗整理而來。
> 涵蓋 2Pack、AD_Field、OSGi、ModelFactory、常見陷阱與排查方法。

---

## 目錄

1. [Plugin 基本架構](#1-plugin-基本架構)
2. [2Pack（PackOut.xml）完整規範](#2-2packpackoutxml-完整規範)
3. [標準 AD_Field 集合（每個 Tab 必備）](#3-標準-ad_field-集合每個-tab-必備)
4. [Model 類別開發規範](#4-model-類別開發規範)
5. [OSGi DS 服務註冊](#5-osgi-ds-服務註冊)
6. [Bundle Activator 與 afterPackIn](#6-bundle-activator-與-afterpackin)
7. [部署與熱更新](#7-部署與熱更新)
8. [除錯：欄位全灰無法編輯](#8-除錯欄位全灰無法編輯)
9. [除錯：Bundle 啟動失敗](#9-除錯bundle-啟動失敗)
10. [升版策略（2Pack Version Bump）](#10-升版策略2pack-version-bump)
11. [常用 DB 查詢速查](#11-常用-db-查詢速查)
12. [AD_Process 與 AD_Process_Para 註冊](#12-ad_process-與-ad_process_para-註冊)
13. [SvrProcess 實作模式](#13-svrprocess-實作模式)
14. [iDempiere 物理表在 adempiere schema](#14-idempiere-物理表在-adempiere-schema)
15. [完整重裝 SOP（clean_reinstall.sh）](#15-完整重裝-sopclean_reinstallsh)

---

## 1. Plugin 基本架構

```
my.plugin/
├── META-INF/
│   ├── MANIFEST.MF              ← OSGi bundle 宣告
│   └── 2Pack_1.0.0.zip          ← 2Pack 字典安裝包
├── OSGI-INF/
│   ├── component.xml            ← Activator DS component
│   └── MyModelFactory.xml       ← ModelFactory DS component（獨立檔案）
├── src/my/plugin/
│   ├── MyActivator.java         ← extends Incremental2PackActivator
│   ├── model/
│   │   ├── MyModelFactory.java  ← extends AnnotationBasedModelFactory
│   │   └── MMyTable.java        ← extends PO，加 @Model
│   └── service/                 ← 純 Java 服務層（可單元測試）
├── resources/
│   └── 2pack/my_package/dict/PackOut.xml
└── test/
```

### MANIFEST.MF 必要項目

```
Bundle-SymbolicName: my.plugin
Bundle-Activator: my.plugin.MyActivator
Service-Component: OSGI-INF/*.xml          ← 萬用字元，自動掃所有 component
Import-Package:
 org.adempiere.base;version="[12.0.0,13.0.0)",
 org.adempiere.pipo2,                      ← 無版本限制（pipo2 export without version）
 org.compiere.model;version="[12.0.0,13.0.0)",
 org.compiere.util;version="[12.0.0,13.0.0)"
```

> **陷阱**：`org.adempiere.pipo2` 不可加版本範圍，加了會導致 bundle resolve 失敗。

---

## 2. 2Pack（PackOut.xml）完整規範

### 2.1 ZIP 結構

```
2Pack_1.0.0.zip
└── my_package/
    └── dict/
        └── PackOut.xml
```

`Incremental2PackActivator` 會解壓縮到 `/tmp/`，然後找 `{name}/dict/PackOut.xml`。
**打包時必須從 `my_package/` 的上一層執行：**

```bash
cd resources/2pack
zip -r ../META-INF/2Pack_1.0.0.zip my_package/
```

錯誤範例（多一層）：
```bash
cd resources
zip -r META-INF/2Pack_1.0.0.zip 2pack/   # ← 路徑變成 2pack/my_package/dict/PackOut.xml，找不到
```

### 2.2 PackOut.xml 根元素

```xml
<?xml version="1.0" encoding="UTF-8"?>
<idempiere Name="my_package"
    Version="1.0.0"
    Description="My Plugin"
    CreatedBy="100"
    UpdatedBy="100"
    Created="2026-01-01"
    Updated="2026-01-01">
```

### 2.3 安裝順序（必須嚴格遵守）

```
AD_EntityType → AD_Reference → AD_Table → AD_Column →
AD_Window → AD_Tab → AD_Field → AD_Menu
```

### 2.4 UUID 規則

- **每筆 metadata 記錄**（Table、Column、Window、Tab、Field、Menu）都要有穩定 UUID
- UUID 必須**硬寫死**在 PackOut.xml，不能在 import 時隨機產生（否則每次 reimport 重複建立）
- 新建 UUID 請用 Python `uuid.uuid4()` 生成後固定
- **升版時已存在的 UUID 不可更換**（見第 10 節）

### 2.5 AD_Column 關鍵欄位

| 欄位 | 說明 | `_UU` 欄位特別注意 |
|------|------|-------------------|
| `IsUpdateable` | 框架可否寫入 | **`_UU` 欄位必須 `Y`**，否則 PO 無法寫 UUID，每筆記錄 UUID 為 NULL |
| `IsMandatory` | 必填 | |
| `DefaultValue` | 預設值（字典層，非 DB default） | |

> **`_UU` 欄位 `IsUpdateable` 陷阱**：
> 標準表（C_Tax、C_BPartner）的 `_UU` 欄位都是 `IsUpdateable=Y`。
> 若設為 `N`，新增記錄後 UUID 欄位永遠是 NULL，難以追蹤。

---

## 3. 標準 AD_Field 集合（每個 Tab 必備）

**根本原因**：`GridTab.dataNew()` 只遍歷 `m_fields`（AD_Field 記錄）來初始化 Tab 的環境變數（env context）。若 `AD_Client_ID` 沒有 AD_Field 記錄，`Env.getContextAsInt(ctx, WindowNo, TabNo, "AD_Client_ID")` 回傳 `-1`，`MRole.canUpdate(-1, ...)` 永遠失敗 → **所有欄位全灰**。

### 完整標準欄位集（從 Pack Out、Tax Rate 等標準視窗驗證）

| ColumnName | SeqNo | IsDisplayed | IsReadOnly | 說明 |
|-----------|-------|-------------|------------|------|
| `{Table}_ID`（Primary Key） | 0 | N | — | 隱藏，框架需要 |
| `{Table}_UU` | 0 | N | — | 隱藏，UUID 欄位 |
| `Created` | 0 | N | — | 隱藏，建立時間 |
| `CreatedBy` | 0 | N | — | 隱藏，建立者 |
| `Updated` | 0 | N | — | 隱藏，更新時間 |
| `UpdatedBy` | 0 | N | — | 隱藏，更新者 |
| `AD_Client_ID`（廠商） | 1 | **Y** | **Y** | **顯示且唯讀**，dataNew() 初始化必需 |
| `AD_Org_ID`（組織） | 2 | **Y** | N | **顯示且可編輯**，dataNew() 初始化必需 |
| `IsActive`（啟用） | 3 | **Y** | N | 顯示且可編輯 |

> **核心原則**：SeqNo=0 是隱藏欄位（IsDisplayed=N），SeqNo≥1 才顯示。
> AD_Client_ID 和 AD_Org_ID **一定要顯示**，這是 dataNew() 初始化的前提。

### 為什麼 SeqNo 不用標準的 10/20？

標準視窗用 10（Tenant）、20（Org）。本專案業務欄位也從 SeqNo=10 開始，
為避免衝突，標準欄位用 1/2/3，業務欄位從 10 開始。功能完全相同。

---

## 4. Model 類別開發規範

### 4.1 必要的 `@Model` 標注

```java
import org.adempiere.base.Model;

@Model(table = MMyTable.Table_Name)
public class MMyTable extends PO {
    public static final String Table_Name = "MY_Table";
    public static final int Table_ID = 0;   // 動態，不寫死
    ...
}
```

缺少 `@Model` → `AnnotationBasedModelFactory` 找不到 → 所有 beforeSave/afterSave 不執行。

### 4.2 `initPO()` 正確寫法

```java
@Override
protected POInfo initPO(Properties ctx) {
    int tableId = MTable.getTable_ID(Table_Name);
    if (tableId <= 0) {
        // 2Pack 尚未安裝時會到這裡，正常現象，PO 建構子可處理
        return null;
    }
    return POInfo.getPOInfo(ctx, tableId, get_TrxName());
}
```

**錯誤寫法：**
```java
// ❌ 不存在此多載
return POInfo.getPOInfo(ctx, Table_Name);

// ❌ 不可這樣 guard
if (Table_ID <= 0) return null;  // Table_ID 靜態欄位永遠是 0
```

### 4.3 Boolean 欄位存取

```java
public boolean isMyFlag() {
    Object oo = get_Value(COLUMNNAME_MyFlag);
    if (oo instanceof Boolean) return (Boolean) oo;
    return "Y".equals(oo);
}
```

### 4.4 不可覆寫的方法

`PO.isActive()` 在 iDempiere 12.0 是 `final`，不可覆寫，會 compile error。

---

## 5. OSGi DS 服務註冊

### 5.1 ModelFactory（必須獨立 XML 檔）

```java
@Component(immediate = true, service = IModelFactory.class,
           property = {"service.ranking:Integer=10"})
public class MyModelFactory extends AnnotationBasedModelFactory {
    @Override
    protected String[] getPackages() {
        return new String[]{"my.plugin.model"};
    }
}
```

`OSGI-INF/MyModelFactory.xml`（不可與 Activator 同一個 XML）：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<scr:component xmlns:scr="http://www.osgi.org/xmlns/scr/v1.1.0"
    immediate="true" name="my.plugin.model.MyModelFactory">
    <implementation class="my.plugin.model.MyModelFactory"/>
    <service><provide interface="org.adempiere.base.IModelFactory"/></service>
    <property name="service.ranking" type="Integer" value="10"/>
</scr:component>
```

> **陷阱**：兩個 `<scr:component>` 不可在同一 XML 檔案裡，OSGi DS 只讀第一個。

### 5.2 MANIFEST.MF 使用萬用字元

```
Service-Component: OSGI-INF/*.xml
```

這樣新增 DS component XML 不需改 MANIFEST.MF。

---

## 6. Bundle Activator 與 afterPackIn

### 6.1 extends Incremental2PackActivator

`Incremental2PackActivator` 自動：
- 等待 IDictionaryService 就緒
- 從 bundle 內的 `/META-INF/2Pack_*.zip` 載入
- 比對 `AD_Package_Imp` 版本，決定是否跳過
- 安裝完成後呼叫 `afterPackIn()`

### 6.2 afterPackIn() 中 DB 操作的 trxName

```java
@Override
protected void afterPackIn() {
    // ✅ 正確：auto-commit，null trxName
    int inserted = DB.executeUpdate(sql, (String) null);

    // ❌ 錯誤：PackIn 在背景執行緒，named Trx 無法使用
    // String trxName = Trx.createTrxName("tw");
    // Trx trx = Trx.get(trxName, true);
    // DB.executeUpdate(sql, trxName);  ← 靜默失敗，完全不執行
}
```

**根本原因**：`Incremental2PackActivator` 在獨立執行緒中執行 PackIn，
該執行緒沒有 iDempiere 的 trx context，named transaction 建立後無法提交。

### 6.3 afterPackIn() 典型用途：授予 window 存取權限

```java
String sql =
    "INSERT INTO AD_Window_Access "
    + "  (AD_Window_ID, AD_Role_ID, AD_Client_ID, AD_Org_ID, "
    + "   IsActive, Created, CreatedBy, Updated, UpdatedBy, "
    + "   IsReadWrite, AD_Window_Access_UU) "
    + "SELECT w.AD_Window_ID, r.AD_Role_ID, r.AD_Client_ID, 0, "
    + "       'Y', NOW(), 100, NOW(), 100, "
    + "       'Y', gen_random_uuid() "
    + "FROM AD_Window w CROSS JOIN AD_Role r "
    + "WHERE w.EntityType = 'MY_ET' "
    + "  AND r.IsActive = 'Y' "
    + "  AND NOT EXISTS ("
    + "    SELECT 1 FROM AD_Window_Access x "
    + "    WHERE x.AD_Window_ID = w.AD_Window_ID AND x.AD_Role_ID = r.AD_Role_ID"
    + "  )";
DB.executeUpdate(sql, (String) null);
```

---

## 7. 部署與熱更新

### 7.1 build + copy + OSGi update

```bash
# 1. build
mvn package

# 2. 複製到 plugins 目錄（路徑依安裝環境而定）
cp target/my.plugin-*.jar /path/to/idempiere/plugins/

# 3. OSGi console 熱更新（預設 port 12612，可透過環境變數覆蓋）
ss my.plugin                    # 查詢 bundle ID
update <id> file:///path/to/idempiere/plugins/my.plugin_1.0.0.jar
start <id>
```

> 使用 `deploy.sh` 時可透過環境變數指定路徑：
> ```bash
> IDEMPIERE_PLUGINS=/path/to/idempiere/plugins ./deploy.sh
> ```

### 7.2 確認 2Pack 安裝

```bash
# log 中搜尋（路徑依安裝環境而定）
grep "Pack\|1\.0\." /path/to/idempiere/log/idempiere.*.log
```

### 7.3 安裝後使用者須重新登入

`afterPackIn()` 寫入的 `AD_Window_Access` 在**使用者重新登入後**才生效。
這是 iDempiere session cache 的正常行為。

---

## 8. 除錯：欄位全灰無法編輯

**症狀**：打開視窗，所有欄位都是灰色，點擊也無法輸入。

### 排查順序

**Step 1：確認 log 有無 `AccessTableNoUpdate`**
```bash
grep "AccessTableNoUpdate\|canUpdate\|saveWarning" idempiere.log
```
若有 → 問題在權限/欄位初始化層。

**Step 2：確認 AD_Window_Access 存在**
```sql
SELECT w.Name, r.Name, wa.IsReadWrite
FROM AD_Window_Access wa
JOIN AD_Window w ON wa.AD_Window_ID = w.AD_Window_ID
JOIN AD_Role r ON wa.AD_Role_ID = r.AD_Role_ID
WHERE w.EntityType = 'MY_ET';
```
若無記錄 → `afterPackIn()` 未執行，或 trxName 問題。

**Step 3：確認 AD_Field 有 AD_Client_ID 和 AD_Org_ID**
```sql
SELECT c.ColumnName, f.SeqNo, f.IsDisplayed
FROM AD_Field f
JOIN AD_Column c ON f.AD_Column_ID = c.AD_Column_ID
JOIN AD_Tab t ON f.AD_Tab_ID = t.AD_Tab_ID
JOIN AD_Window w ON t.AD_Window_ID = w.AD_Window_ID
WHERE w.EntityType = 'MY_ET'
  AND c.ColumnName IN ('AD_Client_ID', 'AD_Org_ID');
```
若沒有這兩筆 → 這就是根本原因。
`GridTab.dataNew()` 無法初始化 env context → `MRole.canUpdate()` 收到 AD_Client_ID=-1 → 失敗。

**修復**：在 PackOut.xml 每個 `<AD_Tab>` 加入 AD_Client_ID 和 AD_Org_ID 的 `<AD_Field>`（IsDisplayed=Y, SeqNo≥1）。

### 根本原理

```
dataNew()
  └─ 遍歷 m_fields（= AD_Field 記錄）
      └─ 對每個有 DefaultValue 的欄位呼叫 getDefault()
          └─ 若欄位是 AD_Client_ID → 寫入 Env: ctx[WindowNo][TabNo]["AD_Client_ID"] = clientId

MRole.canUpdate(AD_Client_ID, ...)
  └─ 從 Env 讀取 ctx[WindowNo][TabNo]["AD_Client_ID"]
      └─ 若找不到 → 回傳 CONTEXT_INT_NOTFOUND (-1)
          └─ canUpdate(-1, ...) → false → 欄位灰色
```

---

## 9. 除錯：Bundle 啟動失敗

### 9.1 `org.adempiere.pipo2` resolve 失敗

```
MANIFEST.MF 錯誤寫法：
org.adempiere.pipo2;version="[12.0.0,13.0.0)"   ← pipo2 export without version，範圍不符

正確寫法：
org.adempiere.pipo2                               ← 不加版本限制
```

### 9.2 2Pack 路徑錯誤

```
錯誤：File does not exist: /tmp/2pack/dict/PackOut.xml
原因：zip 打包時多了一層目錄

確認路徑：
unzip -l 2Pack_1.0.0.zip
# 正確：tw_invoice_system/dict/PackOut.xml
# 錯誤：2pack/tw_invoice_system/dict/PackOut.xml
```

### 9.3 同一 XML 有兩個 `<scr:component>`

```
症狀：ModelFactory 服務未被 OSGi 發現，@Model 標注的 PO 無效
原因：一個 XML 只能有一個 scr:component 根元素
修復：拆成兩個獨立 XML 檔案
```

---

## 10. 升版策略（2Pack Version Bump）

### AD_Field UUID 升版限制

`AD_Field` 表有 `UNIQUE(ad_tab_id, ad_column_id)` 約束。

**升版時更換已存在欄位的 UUID 會導致**：
- 2Pack 以新 UUID 找不到舊記錄
- 嘗試 INSERT 新記錄
- 觸發 unique constraint violation
- 安裝失敗

**正確策略**：
- 已存在的欄位 → **保留原 UUID**（2Pack 走 UPDATE path）
- 新增的欄位 → 使用 `uuid4()` 生成新 UUID

```python
import uuid
# 生成後固定寫死，不在 runtime 產生
new_field_uuid = "f48e9a56-60a7-4f3d-b9be-40b3ec689269"
```

### 升版 checklist

- [ ] 版本號升（PackOut.xml `Version=` 和 zip 檔名）
- [ ] 新欄位/Tab 用新 uuid4
- [ ] 既有欄位保留舊 UUID
- [ ] 測試檔案更新 zip 名稱
- [ ] `mvn test` 通過
- [ ] 重新打包 zip（從正確目錄）
- [ ] 部署並確認 log 顯示新版本已安裝

---

## 11. 常用 DB 查詢速查

```sql
-- 確認 bundle 的 2Pack 安裝記錄
SELECT PK_Version, Name, Description, Created
FROM AD_Package_Imp
WHERE Name LIKE '%my%'
ORDER BY Created DESC;

-- 查詢某視窗下所有 Tab 的 AD_Field
SELECT t.Name, c.ColumnName, f.SeqNo, f.IsDisplayed, f.IsReadOnly
FROM AD_Field f
JOIN AD_Column c ON f.AD_Column_ID = c.AD_Column_ID
JOIN AD_Tab t ON f.AD_Tab_ID = t.AD_Tab_ID
JOIN AD_Window w ON t.AD_Window_ID = w.AD_Window_ID
WHERE w.EntityType = 'MY_ET'
ORDER BY t.SeqNo, f.SeqNo;

-- 確認 _UU 欄位 IsUpdateable
SELECT t.TableName, c.ColumnName, c.IsUpdateable
FROM AD_Column c
JOIN AD_Table t ON c.AD_Table_ID = t.AD_Table_ID
WHERE t.TableName LIKE 'MY_%' AND c.ColumnName LIKE '%_UU';

-- 確認 Window Access 已授予
SELECT w.Name, r.Name as RoleName, wa.IsReadWrite
FROM AD_Window_Access wa
JOIN AD_Window w ON wa.AD_Window_ID = w.AD_Window_ID
JOIN AD_Role r ON wa.AD_Role_ID = r.AD_Role_ID
WHERE w.EntityType = 'MY_ET' AND wa.IsActive = 'Y';

-- 查詢標準表的欄位 pattern（開發時對照用）
SELECT f.Name, c.ColumnName, f.SeqNo, f.IsDisplayed, f.IsReadOnly
FROM AD_Field f
JOIN AD_Column c ON f.AD_Column_ID = c.AD_Column_ID
JOIN AD_Tab t ON f.AD_Tab_ID = t.AD_Tab_ID
JOIN AD_Window w ON t.AD_Window_ID = w.AD_Window_ID
WHERE w.Name = 'Tax Rate' AND t.SeqNo = 10
ORDER BY f.SeqNo;

-- 確認 Process Access 已授予
SELECT p.Value, r.Name as RoleName, pa.IsReadWrite
FROM AD_Process_Access pa
JOIN AD_Process p ON pa.AD_Process_ID = p.AD_Process_ID
JOIN AD_Role r ON pa.AD_Role_ID = r.AD_Role_ID
WHERE p.EntityType = 'MY_ET' AND pa.IsActive = 'Y';
```

---

## 12. AD_Process 與 AD_Process_Para 註冊

### 12.1 PackOut.xml 中 AD_Process 的位置

`AD_Process` 放在 `<AD_Menu>` 區段之前，`<AD_Window>` 區段之後：

```xml
<!-- AD_Window 區段 ... -->

<AD_Process type="table">
  <AD_Process_UU>cc001001-0000-0000-0000-000000000001</AD_Process_UU>
  <Value>TW_GenerateTaxStatement</Value>
  <Name>Generate Tax Statement</Name>
  <ClassName>tw.idempiere.invoice.tax.process.GenerateTaxStatementProcess</ClassName>
  <EntityType>TW</EntityType>
  <IsActive>true</IsActive>
  <IsReport>false</IsReport>
  <IsDirectPrint>false</IsDirectPrint>
  <ShowHelp>N</ShowHelp>
  <AD_Process_Para type="table">
    <AD_Process_ID reference="uuid" reference-key="AD_Process">cc001001-0000-0000-0000-000000000001</AD_Process_ID>
    <AD_Process_Para_UU>cc001011-0000-0000-0000-000000000011</AD_Process_Para_UU>
    <Name>Statement Year</Name>
    <ColumnName>StatementYear</ColumnName>
    <SeqNo>10</SeqNo>
    <AD_Reference_ID reference="id" reference-key="AD_Reference">11</AD_Reference_ID>
    <FieldLength>10</FieldLength>
    <IsMandatory>true</IsMandatory>
    <IsRange>false</IsRange>
    <EntityType>TW</EntityType>
    <IsActive>true</IsActive>
  </AD_Process_Para>
</AD_Process>
```

### 12.2 兩個必填欄位（踩坑記錄）

**陷阱 1：`AD_Process_ID` 必須明確寫入每個 `AD_Process_Para`**

`ProcessParaElementHandler` **不會**從父元素 `<AD_Process>` 繼承 `AD_Process_ID`，
不像 `AD_Tab` 可以從 `AD_Window` 繼承 `AD_Window_ID`。

```xml
<!-- 錯誤：缺少 AD_Process_ID → 存入時 NOT NULL 違規 -->
<AD_Process_Para type="table">
  <Name>Year</Name>
  ...
</AD_Process_Para>

<!-- 正確：每個 para 都要顯式宣告 -->
<AD_Process_Para type="table">
  <AD_Process_ID reference="uuid" reference-key="AD_Process">{process-uuid}</AD_Process_ID>
  <Name>Year</Name>
  ...
</AD_Process_Para>
```

**陷阱 2：`FieldLength` NOT NULL**

```xml
<!-- 依 AD_Reference_ID 選填 -->
<!-- Integer (11)  → FieldLength = 10  -->
<!-- List    (17)  → FieldLength = 1   -->
<!-- String  (10)  → FieldLength = 實際最大長度 -->
<FieldLength>10</FieldLength>
```

**除錯方式：** iDempiere log 只會說 `Failed to save ProcessPara`，真正的 SQL 錯誤需查 PostgreSQL log：
```bash
tail -f /var/log/postgresql/postgresql-16-main.log
```

### 12.3 AD_Menu 中的 Process 連結

```xml
<AD_Menu type="table">
  <AD_Menu_UU>cc009001-0000-0000-0000-000000000009</AD_Menu_UU>
  <Name>Generate Tax Statement</Name>
  <Action>P</Action>      <!-- P = Process，W = Window -->
  <AD_Process_ID reference="uuid" reference-key="AD_Process">cc001001-0000-0000-0000-000000000001</AD_Process_ID>
  <EntityType>TW</EntityType>
  <IsActive>true</IsActive>
  <IsSummary>false</IsSummary>
</AD_Menu>
```

---

## 13. SvrProcess 實作模式

### 13.1 基本結構

```java
public class MyProcess extends SvrProcess {

    // Parameters declared as instance fields
    private int myYear;
    private int myPeriod;

    @Override
    protected void prepare() {
        ProcessInfoParameter[] para = getParameter();
        for (ProcessInfoParameter p : para) {
            String name = p.getParameterName();
            if ("MyYear".equals(name))
                myYear = p.getParameterAsInt();
            else if ("MyPeriod".equals(name))
                myPeriod = p.getParameterAsInt();
        }
    }

    @Override
    protected String doIt() throws Exception {
        // ... 實作業務邏輯 ...
        return "@Process@ @OK@";   // 回傳訊息（ad_message 鍵或純文字）
    }
}
```

### 13.2 用 DB.prepareStatement 做 SQL 聚合

```java
String sql = "SELECT SUM(c.GrandTotal) FROM TW_Invoice_Prefix_Map m "
           + "JOIN C_Invoice c ON m.C_Invoice_ID = c.C_Invoice_ID "
           + "WHERE m.AD_Client_ID = ? AND m.StatementYear = ? "
           + "  AND m.StatementPeriod = ? AND c.IsSOTrx = 'Y'";

PreparedStatement ps = null;
ResultSet rs = null;
try {
    ps = DB.prepareStatement(sql, get_TrxName());
    ps.setInt(1, getAD_Client_ID());
    ps.setInt(2, year);
    ps.setInt(3, period);
    rs = ps.executeQuery();
    if (rs.next()) {
        BigDecimal total = rs.getBigDecimal(1);
        // ...
    }
} finally {
    DB.close(rs, ps);
}
```

注意：**不要**呼叫 `ps.setAD_Client_ID()` — 那是 PO 類別的 protected 方法，不是 PreparedStatement 的方法。PO 建構子會自動從 context 設定 AD_Client_ID。

### 13.3 取得目前 Client/Org 的方式

在 `SvrProcess` 內：
```java
int clientId = getAD_Client_ID();   // 來自 ProcessInfo
int orgId    = getAD_Org_ID();      // 來自 ProcessInfo
```

---

## 14. iDempiere 物理表在 adempiere schema

iDempiere 12.0 的物理表建立在 PostgreSQL 的 **`adempiere`** schema，不是 `public`。

這影響：

**1. SQL 查詢表是否存在**
```sql
-- 錯誤：public schema 找不到 TW_* 表
SELECT count(*) FROM information_schema.tables
WHERE table_schema = 'public' AND table_name ILIKE 'TW_%';

-- 正確：用 pg_tables 查 adempiere schema
SELECT tablename FROM pg_tables
WHERE schemaname = 'adempiere' AND tablename ILIKE 'tw_%';
```

**2. DROP TABLE 語法**
```sql
-- 錯誤：找不到表
DROP TABLE IF EXISTS TW_InvoicePrefix;

-- 正確：加 schema prefix
DROP TABLE IF EXISTS adempiere.TW_InvoicePrefix;
```

**3. psql 直連確認**
```bash
psql -U adempiere -d idempiere -c "\dt adempiere.TW_*"
```

---

## 15. 完整重裝 SOP（clean_reinstall.sh）

開發期間若需要從頭清除並重新部署，用專案根目錄的 `clean_reinstall.sh`：

```bash
./clean_reinstall.sh
```

腳本執行流程：

1. **移除 TW 字典（FK 順序）**
   Window_Access → Process_Access → Field_Trl → Field → Tab_Trl → Tab → Menu → Window_Trl → Window → Process_Para → Process

2. **Drop 物理表**（使用 `adempiere.TW_*` prefix）

3. **移除後設資料**
   Column → Table_Trl → Table → Sequence → Element_Trl → Element → Ref_List → Reference → EntityType

4. **清除安裝記錄**
   Package_Imp_Detail 先刪（有 FK），再刪 Package_Imp

5. **呼叫 deploy.sh** 重新編譯並部署

### 重裝後必做

- 所有使用者需**重新登入** iDempiere 才能取得新權限
- `afterPackIn()` 會自動授予 Window Access 和 Process Access

### 注意

- `AD_Ref_List` 刪除條件是 `WHERE AD_Reference_ID IN (SELECT ... WHERE Name LIKE 'TW_%')`
  不是 `WHERE EntityType = 'TW'`（2Pack 安裝後 Ref_List 的 EntityType 可能是 `U`）
- `AD_Package_Imp_Detail` 必須在 `AD_Package_Imp` 之前刪除（FK 約束）
