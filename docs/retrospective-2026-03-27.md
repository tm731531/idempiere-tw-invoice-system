# 開發反省與教學手冊 — 2026-03-27

> 台灣統一發票系統（`tw.idempiere.invoice.tax`）近兩天開發過程的完整記錄。
> 涵蓋所有遭遇的 bug、根本原因、修復方式，以及正確的 SOP。
> 這份文件的目的：讓每個痛苦的教訓只犯一次。

---

## 目錄

1. [2Pack / PackOut.xml 錯誤（7個）](#1-2pack--packoutxml-錯誤)
2. [Java 程式碼 Bug（7個）](#2-java-程式碼-bug)
3. [OSGi / 部署環境問題（5個）](#3-osgi--部署環境問題)
4. [資料庫清除與新裝陷阱（5個）](#4-資料庫清除與新裝陷阱)
5. [Git 操作失誤（1個）](#5-git-操作失誤)
6. [程式碼審查流程的反省](#6-程式碼審查流程的反省)
7. [正確工作流程 SOP](#7-正確工作流程-sop)
8. [下次開發前必讀清單](#8-下次開發前必讀清單)

---

## 1. 2Pack / PackOut.xml 錯誤

### 1-A：ZIP 目錄結構錯誤（犯了兩次）

**第一次（1.0.8版）**：ZIP 打包時路徑包含 `2pack/` 前綴，導致：
```
錯誤：File does not exist: /tmp/2pack/tw_invoice_system/dict/PackOut.xml
```

**第二次（1.0.10版升版時）**：用了 `zip -j`（junk paths），路徑被完全剝掉：
```
錯誤：File does not exist: /tmp/PackOut.xml/dict/PackOut.xml
```

iDempiere 解壓 ZIP 到 `/tmp/`，預期路徑為 `/tmp/tw_invoice_system/dict/PackOut.xml`。

**正確打包方式（每次必做）：**
```bash
mkdir -p /tmp/build/tw_invoice_system/dict
cp PackOut.xml /tmp/build/tw_invoice_system/dict/
cd /tmp/build
zip -r /path/to/2Pack_1.0.xx.zip tw_invoice_system/
rm -rf /tmp/build

# 打包後一定要驗證
unzip -l /path/to/2Pack_1.0.xx.zip
# 必須看到：
# tw_invoice_system/
# tw_invoice_system/dict/
# tw_invoice_system/dict/PackOut.xml
```

---

### 1-B：AD_Field UUID 衝突 → POSaveFailedException

**症狀**：2Pack 安裝時拋出 `POSaveFailedException`，錯誤指向 Tenant（AD_Client_ID）欄位的 AD_Field 記錄。

**根本原因**：`AD_Field` 有 `UNIQUE(ad_tab_id, ad_column_id)` 約束。2Pack 初次安裝沒問題，但若 iDempiere 已自動為新 Tab 建立了標準欄位（如 AD_Client_ID、AD_Org_ID、IsActive），再次 PackIn 時用不同 UUID 插入同一個 (tab_id, column_id) 組合，就會違反 unique constraint。

**解法：Placeholder UUID 策略**

對已存在的標準欄位（AD_Client_ID、AD_Org_ID、IsActive、Created 等），PackOut.xml 中的 `<AD_Field_UU>` 使用**不會衝突的 placeholder 值**（如 `00000000-0000-0000-0000-000000000001`）。PackIn 遇到重複時會 UPDATE 而不是 INSERT，就不會違反 unique constraint。

**升版時的 UUID 規則：**
- 已存在的 field（前版 2Pack 就有的）→ 保留原 UUID，不要改
- 新增的 field（本版才加的）→ 生成新的 uuid4
- 改了 UUID 等於換了一個新 field，舊的不會被刪，造成重複

---

### 1-C：`_UU` 欄位 IsUpdateable=N → UUID 永遠 NULL

**症狀**：新建記錄後查 DB，`TW_InvoicePrefix_UU` 欄位永遠是 NULL。

**根本原因**：PackOut.xml 中 `_UU` 欄位的 `<IsUpdateable>N</IsUpdateable>`。iDempiere PO 框架在儲存新記錄時會自動 SET `_UU` 欄位，但 `IsUpdateable=N` 表示框架視此欄為唯讀，不會寫入值。

**驗證（安裝後立刻跑）：**
```sql
SELECT columnname, isupdateable
FROM ad_column
WHERE ad_table_id IN (
    SELECT ad_table_id FROM ad_table WHERE tablename LIKE 'TW_%'
)
AND columnname LIKE '%_UU';
-- 所有 _UU 欄位必須是 isupdateable='Y'
```

**標準設定（同 C_Tax、C_BPartner）：**
```xml
<IsUpdateable>Y</IsUpdateable>
```

---

### 1-D：SeqNoGrid 完全缺失 → Index:2 Grid View 崩潰

**症狀**：四個 TW 視窗按「新增」立刻跳錯 `IndexOutOfBoundsException: Index: 2`，系統顯示紅色錯誤對話框。

**根本原因**：PackOut.xml 的 73 個 `<AD_Field>` 元素全部缺少 `<SeqNoGrid>` 和正確的 `<IsDisplayedGrid>`。iDempiere 12 的 Grid View 渲染器在 `editCurrentRow()` 初始化行編輯器時讀取 `SeqNoGrid` 做排序依據，全 NULL 導致內部列表 `list.get(2)` 越界。

診斷：
```sql
SELECT tablename, count(*) FILTER (WHERE f.seqnogrid > 0) as ok,
       count(*) FILTER (WHERE f.seqnogrid IS NULL OR f.seqnogrid = 0) as bad
FROM ad_table t JOIN ad_tab tab ON tab.ad_table_id = t.ad_table_id
JOIN ad_field f ON f.ad_tab_id = tab.ad_tab_id
WHERE t.tablename LIKE 'TW_%' GROUP BY tablename;
-- 所有 displayed field 的 ok 必須 > 0，bad 只能是隱藏欄位的數量
```

**正確設定：**
```xml
<!-- 顯示欄位 -->
<SeqNo>10</SeqNo>
<SeqNoGrid>10</SeqNoGrid>        ← 與 SeqNo 相同
<IsDisplayedGrid>Y</IsDisplayedGrid>
<IsDisplayed>Y</IsDisplayed>

<!-- 隱藏欄位（PK、_UU、Created...） -->
<SeqNo>0</SeqNo>
<SeqNoGrid>0</SeqNoGrid>
<IsDisplayedGrid>N</IsDisplayedGrid>
<IsDisplayed>N</IsDisplayed>
```

---

### 1-E：JaCoCo per-class 規則參照不存在的類別

**症狀**：沒有立即錯誤，但 coverage 規則完全失效。

**根本原因**：`pom.xml` 的 JaCoCo 設定參照了 Phase 1 開發時的類別（`AccountingCodeMapper`、`TaxStatementValidator` 舊版路徑、`InvoiceAdjustmentValidator` 等），這些類後來在「清除 Phase 1 程式碼」的 commit 中被刪除，但 pom.xml 沒有跟著更新。

**教訓**：刪程式碼時一定要搜尋 pom.xml 中有沒有對應的 JaCoCo、maven-compiler-plugin、shade plugin 參照。

---

### 1-F：PackOut.xml 中存在過時的 `tw_invoice_system.zip`

`resources/2pack/tw_invoice_system.zip` 是早期 v1.0.0 的遺留物，比 `META-INF/2Pack_1.0.9.zip` 少了 79% 的內容。雖然 pom.xml 已設定排除此目錄（`2pack/**`），但留著容易混淆。已清除。

---

### 1-G：gen_packout.py 生成腳本多個問題

`/tmp/gen_packout.py` 在這次開發中需要多輪修正：
- `_UU` 欄位的 `IsUpdateable` 旗標設錯
- `<SeqNoGrid>` 和 `<IsDisplayedGrid>` 從未被生成（造成 1-D 問題）
- Placeholder UUID 策略需要手動加入

**教訓**：生成腳本是 PackOut.xml 的「唯一真相來源」，生成後必須用 DB 驗證結果，不能只看 XML。

---

## 2. Java 程式碼 Bug

### 2-A：事件主題用字串後綴比較（Critical）

**症狀**：`PO_BEFORE_CHANGE` 事件從不觸發，狀態轉換守衛靜默失效。

```java
// 錯誤
if (topic.endsWith("po_before_change")) { ... }

// 正確
if (IEventTopics.PO_BEFORE_CHANGE.equals(topic)) { ... }
```

iDempiere 的 topic 格式是 `org/adempiere/po/PO_BEFORE_CHANGE`（大寫），`endsWith` 比較大小寫不同的後綴永遠不匹配。

---

### 2-B：A→I 狀態轉換未攔截（Critical）

字軌狀態機只攔截了 `C → 任何`，忘記攔截 `A → I`。

```java
// 需要明確攔截的所有非法轉換
if ("A".equals(old) && "I".equals(newS)) return "使用中字軌不可降回未啟用（台灣稅法）";
if ("C".equals(old)) return "已用完字軌不可變更狀態";
```

**設計建議**：寫狀態機時先畫「禁止轉換矩陣」（N×N 格），而不是只想「允許哪些」。

---

### 2-C：ModelValidator 實作從未被呼叫（Critical Dead Code）

`InvoicePrefixValidator` 和 `InvoicePrefixMapValidator` 都實作了 `ModelValidator` 介面的全部方法（`initialize`、`modelChange`、`docValidate`...），但這些方法**從未被呼叫過**。

**原因**：iDempiere 的 `ModelValidator` 需要透過 `ModelValidationEngine.addModelValidator(validator, client)` 主動登錄，或透過特定的 OSGi tracker 機制。單純 `implements ModelValidator` 什麼都不會發生。

**本 plugin 的正確架構**：
```
*Validator.java    ← 純靜態方法，不實作任何介面
*EventHandler.java ← extends AbstractEventHandler，OSGI-INF/*.xml 登錄
```

---

### 2-D：時區不安全的日期轉換

```java
// 錯誤（假設 JVM 時區 = DB 時區，在伺服器換時區後會差一天）
LocalDate d = ts.toLocalDate();

// 正確（明確使用系統時區，可預期）
LocalDate d = ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
```

---

### 2-E：System.err.println 用於正常日誌

```java
// 錯誤
System.err.println("PackIn installing: " + zipPath);

// 正確
logger.info("PackIn installing: " + zipPath);
```

`System.err` 輸出會出現在 stderr 而非 iDempiere 的 log 檔，難以追蹤。

---

### 2-F：Model 類別缺少 COLUMNNAME_*_UU 常數

四個 Model 類別（`MInvoicePrefix`、`MInvoicePrefixMap`、`MInvoiceAdjustment`、`MTaxStatement`）都缺少 `_UU` 欄位的常數定義，導致 type-safe 方式存取 UUID 欄位時需要硬寫字串。

```java
// 補上這個
public static final String COLUMNNAME_TW_InvoicePrefix_UU = "TW_InvoicePrefix_UU";
```

---

### 2-G：Stub 流程回傳假成功

```java
// 錯誤 — 讓使用者以為流程執行成功
protected String doIt() throws Exception {
    return "Success";
}

// 正確 — 明確表達尚未實作
protected String doIt() throws Exception {
    throw new UnsupportedOperationException(
        "GenerateTaxStatementProcess is not yet implemented. Use manual entry.");
}
```

假成功比報錯更危險，使用者不知道申報表根本沒被產生。

---

## 3. OSGi / 部署環境問題

### 3-A：孤立的 OSGI-INF XML 檔案

`OSGI-INF/com.trekglobal.idempiere.rest.api.model.RESTEventHandler.xml` 是外部 bundle（REST API plugin）的元件描述，不知道何時被帶進我們的 plugin。這個 XML 讓 OSGi 以為我們的 bundle 提供了一個 RESTEventHandler DS 服務，但對應的類別根本不在我們的 JAR 裡，造成服務啟動失敗。

**防範**：每次 `ls OSGI-INF/` 確認只有自己的元件描述。

---

### 3-B：孤立的 plugin.xml

專案根目錄殘留了一個 `plugin.xml`，這是 Eclipse pipo bundle 工具的產物，對 Maven 建置無意義且可能混淆 IDE 工具鏈。已刪除。

---

### 3-C：deploy.sh 預設路徑是佔位符

```bash
# 錯誤（不可用）
PLUGIN_DIR="${IDEMPIERE_PLUGINS:-/path/to/idempiere/plugins}"

# 正確（實際預設值）
PLUGIN_DIR="${IDEMPIERE_PLUGINS:-/opt/idempiere-server/x86_64/plugins}"
```

---

### 3-D：雙 JVM 互搶 port

**症狀**：OSGi telnet 顯示 bundle ACTIVE，但 Web Console 找不到這個 bundle。

**根本原因**：`idempiere-server.sh` 有 restart loop。舊 JVM 還活著時，systemctl restart 又啟動了一個新 JVM。兩個 JVM 各搶到不同 port：
- JVM-舊（PID-A）：port 8080/8443（Web Console 連這個）
- JVM-新（PID-B）：port 12612（telnet 連這個）

deploy.sh 透過 telnet 把 bundle 裝進了 JVM-新，Web Console 連的 JVM-舊根本不知道。

**診斷**：
```bash
ps aux | grep java | grep -v grep | wc -l   # 如果 > 1，就是這個問題
ss -tlnp | grep -E "8080|8443|12612"
```

**修復**：kill 兩個舊 JVM，再 systemctl restart 一次，等 8080 port 出現再部署。

---

### 3-E：Jetty 第一次重啟失敗

殺掉舊 JVM 後第一次 restart，Jetty 仍然報 `Failed to start server FAILED[sto=5000]`。原因是舊 JVM 的 port 剛被 kernel 釋放但 TIME_WAIT 狀態還沒清除，Jetty 試圖 bind 8080 失敗。

**解法**：等 30 秒後再 restart 一次。第二次成功。

---

## 4. 資料庫清除與新裝陷阱

（在「完整卸載 → 清 DB → 重裝」的驗證流程中遭遇）

### 4-A：多條 SQL 在同一個 `-c` 中執行 → 全部 Rollback

```bash
# 錯誤 — 任何一條失敗，整個 -c 就 rollback
psql ... -c "DELETE FROM ad_field WHERE ...; DELETE FROM ad_tab WHERE ...;"

# 正確 — 每條 SQL 獨立 transaction
psql ... -c "DELETE FROM ad_field WHERE entitytype='TW';"
psql ... -c "DELETE FROM ad_tab WHERE entitytype='TW';"
```

---

### 4-B：錯誤的資料表名稱

```bash
# 錯誤
SELECT * FROM ad_package_imp_det WHERE ...

# 正確
SELECT * FROM ad_package_imp_detail WHERE ...
```

---

### 4-C：FK 違反阻擋刪除順序

清除 TW 相關資料時，各資料表間有 FK 約束，必須按正確順序刪：

```sql
-- 必須先刪子表再刪父表
DELETE FROM ad_ref_list      WHERE entitytype='TW';  -- 先於 ad_reference
DELETE FROM ad_field         WHERE entitytype='TW';  -- 先於 ad_tab
DELETE FROM ad_tab           WHERE entitytype='TW';  -- 先於 ad_window
DELETE FROM ad_preference    WHERE ad_window_id IN (...);  -- 先於 ad_window
DELETE FROM ad_menu          WHERE ad_window_id IN (...);  -- 先於 ad_window
DELETE FROM ad_window        WHERE entitytype='TW';
DELETE FROM ad_column        WHERE entitytype='TW';
DELETE FROM ad_table         WHERE entitytype='TW';
DELETE FROM ad_entitytype    WHERE entitytype='TW';
-- 最後才能刪物理表
DROP TABLE IF EXISTS TW_InvoicePrefix, TW_Invoice_Prefix_Map, ...;
```

**教訓**：清除前先用 `\d+ tablename` 查 FK 約束，從最末端的子表開始刪。

---

### 4-D：PackOut.xml 巢狀結構的誤解

iDempiere 專家審查時認為「AD_Table 定義放在 AD_Menu 節點下不會被 PackIn 處理」。

**實際驗證結果：錯的。**

PackIn 的 SAX parser 會遍歷整個 XML 樹，無論元素巢狀在哪個節點下都會被處理。DB 查詢確認 4 個 TW_* 資料表、73 個欄位全部正確建立。

**教訓**：對專家意見持懷疑態度，用 DB 查詢驗證比口頭討論可靠。

---

### 4-E：2Pack 安裝記錄未清除導致重裝無效

`ad_package_imp` 和 `ad_package_imp_detail` 記錄了 2Pack 安裝歷史。若只刪了 AD_Table/AD_Field 等定義但沒清安裝記錄，`Incremental2PackActivator` 判定「已安裝過同版本」就會跳過，不重新安裝。

完整清除時需加：
```sql
DELETE FROM ad_package_imp_detail WHERE ad_package_imp_id IN (
    SELECT ad_package_imp_id FROM ad_package_imp WHERE name LIKE '%tw_invoice%'
);
DELETE FROM ad_package_imp WHERE name LIKE '%tw_invoice%';
```

---

## 5. Git 操作失誤

### 5-A：直接 push 到 main

開發過程中不小心在 main 分支上 commit 並 push。

**補救**：
```bash
git checkout dev
git cherry-pick <sha1> <sha2>   # 把 commit 搬到 dev
git push origin dev
# main 不動，讓它停在上一個穩定狀態
```

**根本原因**：沒有在每次 commit 前確認分支。

**防範**：
```bash
# 每次 push 前
git branch          # 確認在 dev 或 feature/*
git push origin dev # 明確指定 target，不用裸 git push
```

**鐵則（已存入記憶體）**：
- `dev` = 所有日常工作
- `main` = 穩定版，只有 dev 達到穩定後才 merge 進去
- **永遠不直接 commit 或 push main**

---

## 6. 程式碼審查流程的反省

### 這次審查了幾輪？

| 輪次 | 類型 | 發現問題數 |
|------|------|-----------|
| Round 1 | 一般審查 | 3 Critical + 5 Important + 4 Minor |
| Round 2 | 深度審查 | 2 Critical + 5 Important + 4 Minor |
| Round 3 | 驗證審查 | 1 Important + 2 Suggestions |
| Expert-1 | iDempiere 框架專家 | PackOut.xml 巢狀（後驗證為誤） + 其他 |
| Expert-2 | OSGi 專家 | 舊 JAR 問題（已確認） |
| Expert-3 | 台灣稅務專家 | 多個法規合規缺口（記錄為 TODO） |

### 為什麼要審查三輪才找完？

每一輪修復會引入新的問題或暴露被遮蔽的問題。Round 1 修復事件 topic 後，Round 2 才能看清楚 timezone 問題；Round 2 修完後 Round 3 才能驗證測試覆蓋。

**教訓**：不要期望一輪審查就能找完所有問題。計劃至少 2 輪。

### Expert Review 的價值與限制

- **價值**：領域專家能看到框架特定的坑（如 OSGi 舊 JAR）
- **限制**：不能直接存取 DB，所以有時候判斷錯誤（如 PackOut.xml 巢狀問題）
- **原則**：專家意見作為 input，最終以 DB 查詢結果為準

### 台灣稅務合規缺口（已記錄為 TODO，非 bug）

以下功能驗證出有合規缺口，但屬計劃外功能，已記錄：

| 問題 | 影響 |
|------|------|
| 兼營比例分母未定義（總銷售還是含零稅率？） | 兼營業者比例計算可能錯誤 |
| `FilingDueDate` 未自動計算 | 申報截止日需手動填入 |
| `IsOverduePeriod` 未自動判定 | 超期折讓需使用者手動勾選 |
| `GenerateTaxStatementProcess` 未實作 | 401申報表只能手動建立 |
| `ExportTaxReportProcess` 未實作 | 電子申報 CSV 無法匯出 |
| 發票號碼連號鎖（SELECT FOR UPDATE）未驗證 | 高並發環境可能重號 |

---

## 7. 正確工作流程 SOP

### 2Pack 修改 → 部署流程

```
1. 修改 PackOut.xml
2. 打包 ZIP（驗證目錄結構！）
   mkdir -p /tmp/b/tw_invoice_system/dict
   cp PackOut.xml /tmp/b/tw_invoice_system/dict/
   cd /tmp/b && zip -r 2Pack_X.X.XX.zip tw_invoice_system/
   unzip -l 2Pack_X.X.XX.zip   ← 必做
3. 刪舊 ZIP，放新 ZIP
4. 更新 ActivatorPackInTest.java 的 PACK_VERSION
5. mvn clean package（確認測試全過）
6. 確認只有一個 JVM: ps aux | grep java | grep -v grep | wc -l
7. ./deploy.sh
8. 等 log: "...2Pack_X.X.XX.zip installed"
9. DB 驗證:
   - SeqNoGrid > 0 的欄位數
   - IsUpdateable='Y' 的 _UU 欄位
10. Cache Reset（iDempiere 左側選單）或重登
11. 手動測試：每個視窗的新增、儲存、查詢
```

### Release 流程

```
1. 確認 dev 所有測試通過 + 文件更新
2. git tag -a v1.0.xx -m "Release notes..."
3. git push origin dev && git push origin v1.0.xx
4. gh release create v1.0.xx target/*.jar --title "..." --notes "..."
```

---

## 8. 下次開發前必讀清單

### PackOut.xml 修改前

- [ ] 每個 `<AD_Field>` 有 `<SeqNoGrid>`（顯示欄位 = SeqNo，隱藏欄位 = 0）
- [ ] 每個 `<AD_Field>` 有 `<IsDisplayedGrid>`（顯示欄位 Y，隱藏欄位 N）
- [ ] `_UU` 欄位有 `<IsUpdateable>Y</IsUpdateable>`
- [ ] 升版時，已存在的 field 保留原 UUID，只有新 field 才用新 uuid4

### 打包前

- [ ] `unzip -l 2Pack_X.X.XX.zip` 確認路徑為 `tw_invoice_system/dict/PackOut.xml`
- [ ] `ActivatorPackInTest.java` 的 PACK_VERSION 已更新

### 部署前

- [ ] `ps aux | grep java | grep -v grep | wc -l` 輸出為 1
- [ ] `ss -tlnp | grep 8080` 有值（Jetty 在跑）
- [ ] 確認在 `dev` 或 `feature/*` 分支

### 部署後

- [ ] Log 出現 `...installed` 而不是 `Pack in failed`
- [ ] DB 查詢確認 `seqnogrid`、`isdisplayedgrid`、`isupdateable` 值正確
- [ ] Cache Reset 或重新登入
- [ ] 手動測試四個視窗的新增 + 儲存（最容易踩到 Grid View 問題）

### Java 程式碼

- [ ] 事件主題比較用 `IEventTopics.CONSTANT.equals(topic)`
- [ ] Validator 類別只有靜態方法，不實作 ModelValidator
- [ ] 每個 EventHandler 在 `OSGI-INF/` 下有對應的 XML 元件描述檔
- [ ] `ls OSGI-INF/` 只有自己的 XML（沒有外來 bundle 的殘留）
- [ ] 未實作的流程拋 `UnsupportedOperationException`，不回傳假成功

### Git

- [ ] `git branch` 確認在 dev 或 feature/*
- [ ] push 時明確指定：`git push origin dev`
