# 用兩天踩遍所有坑：為 iDempiere 開發台灣統一發票 OSGi Plugin

> 這篇文章記錄了我們為 iDempiere 12.0 從零開發台灣統一發票與營業稅申報 plugin 的過程——包括設計決策、遇到的每一個坑、以及最後讓它正常運作的關鍵細節。
> 如果你正在開發 iDempiere plugin，這篇文章能幫你少掉至少一天的除錯時間。

---

## 為什麼要做這個 Plugin？

台灣的統一發票制度獨特而嚴格：每兩個月一期的雙月申報週期、財政部核配的字軌號碼（如 AA01234567）、三聯式（B2B）和二聯式（B2C）的不同計稅方式、以及嚴格的 FLOOR 取捨規定。

這些都是 iDempiere 標準功能完全沒有覆蓋的。既有的 C_Invoice 系統不知道什麼是「字軌」，不知道進項折讓要在哪一期申報，更不知道 401 申報表長什麼樣。

所以我們做了這個 plugin。

---

## 系統架構概覽

四張核心資料表，對應台灣發票生命週期的四個階段：

```
財政部核配字軌
      │
      ▼
TW_InvoicePrefix      ← 字軌管理（AA、AB 等，號碼範圍、有效期）
      │ 開立發票時
      ▼
TW_Invoice_Prefix_Map ← 每張發票的字軌號碼對應（含買方統一編號）
      │ 如有退貨/折讓
      ▼
TW_InvoiceAdjustment  ← 銷項/進項折讓（方向、期別、超期申報）
      │ 期末
      ▼
TW_TaxStatement       ← 401 申報表（銷項稅、進項稅、留抵、應納稅額）
```

技術層面：OSGi bundle（Equinox），2Pack 管理 dictionary，AbstractEventHandler 做 PO 事件驗證，服務層純 Java 方便單元測試。

---

## 台灣稅法翻成程式碼

### 稅額計算：FLOOR，不是 ROUND

這是台灣財政部的明確規定，一律捨去，不四捨五入：

```java
// 二聯式（B2C）：含稅金額已知，反推銷售額和稅額
BigDecimal saleAmount = grossAmount
    .divide(new BigDecimal("1.05"), 0, RoundingMode.FLOOR);
BigDecimal taxAmount = saleAmount
    .multiply(new BigDecimal("0.05"))
    .setScale(0, RoundingMode.FLOOR);

// 注意：taxAmount ≠ grossAmount - saleAmount
// 在某些邊界值，兩種算法差一塊錢
// 財政部規定：用前者（先算銷售額，再乘 5%）
```

這個細節在單元測試裡用邊界值確認，差一塊不是小事，申報出去就是對不上帳。

### 字軌狀態機：單向前進

```
I（未啟用）→ A（使用中）→ C（已用完）
```

**禁止的轉換**：
- `A → I`：已啟用字軌不可撤回（台灣稅法要求追蹤已啟用的字軌）
- `C → 任何`：終態，不可逆

```java
// EventHandler 在 PO_BEFORE_CHANGE 攔截
if ("A".equals(oldStatus) && "I".equals(newStatus))
    throw new AdempiereException("使用中字軌不可降回未啟用（台灣稅法）");
if ("C".equals(oldStatus))
    throw new AdempiereException("已用完字軌不可再變更狀態");
```

### 申報期別：雙月制

```java
// 從發票月份算期別
int period = (month - 1) / 2 + 1;  // 1=1-2月, 2=3-4月 ... 6=11-12月
```

---

## 踩過的坑（精選重要的）

### 坑 1：事件主題比較，一個字毀掉一切

這個 bug 讓狀態驗證完全靜默失效了很久。

```java
// 錯誤 — 永遠不會觸發
if (topic.endsWith("po_before_change")) { ... }

// 正確
if (IEventTopics.PO_BEFORE_CHANGE.equals(topic)) { ... }
```

iDempiere 的 topic 格式是 `org/adempiere/po/PO_BEFORE_CHANGE`（大寫），`endsWith` 配對小寫後綴當然不匹配。靜默失效最可怕——不報錯，只是所有驗證都沒執行。

**規則**：事件主題永遠用 `IEventTopics` 常數。

---

### 坑 2：ModelValidator 是個陷阱

我們的 validator 類別一開始實作了完整的 `ModelValidator` 介面（`initialize`、`modelChange`、`docValidate`、`login`…），程式碼寫得很整齊，但驗證從來不執行。

原因：`ModelValidator` 需要透過 `ModelValidationEngine.addModelValidator()` 主動登錄。單純 `implements ModelValidator` + `@Component` 什麼都不會發生。

正確的 iDempiere 12 plugin 驗證模式是：

```
*Validator.java    → 純靜態方法，不實作任何介面
*EventHandler.java → extends AbstractEventHandler，OSGI-INF/*.xml 登錄為 DS 服務
```

EventHandler 捕捉 OSGi 事件，呼叫 Validator 的靜態方法做驗證。乾淨、可測試、不依賴 ModelValidationEngine。

---

### 坑 3：2Pack ZIP 打包方式，犯了兩次

iDempiere 的 PackIn 解壓 ZIP 到 `/tmp/`，預期路徑是：
```
/tmp/tw_invoice_system/dict/PackOut.xml
```

**第一次**：ZIP 裡多了 `2pack/` 前綴層。
**第二次**：用了 `zip -j`（junk paths），把所有目錄剝掉，路徑變成：
```
/tmp/PackOut.xml/dict/PackOut.xml  ← 把文件名當目錄了
```

正確打包：
```bash
mkdir -p /tmp/b/tw_invoice_system/dict
cp PackOut.xml /tmp/b/tw_invoice_system/dict/
cd /tmp/b && zip -r 2Pack_1.0.10.zip tw_invoice_system/

# 永遠要驗證結構
unzip -l 2Pack_1.0.10.zip
```

---

### 坑 4：SeqNoGrid 缺失 → Grid View 崩潰

這是整個過程中最「無辜」的 bug——不是邏輯錯誤，是 XML 少了兩個欄位。

所有 73 個 `AD_Field` 元素都缺少 `<SeqNoGrid>` 和正確的 `<IsDisplayedGrid>`。iDempiere 12 的 Grid View 渲染器在初始化行編輯器時需要 `SeqNoGrid` 做排序依據，全部 NULL 導致：

```
java.lang.IndexOutOfBoundsException: Index: 2
    at GridTabRowRenderer.editCurrentRow
```

任何視窗，只要在 Grid 模式下按「新增」，必爆。

修復很簡單，就是補上這兩個欄位：
```xml
<SeqNo>10</SeqNo>
<SeqNoGrid>10</SeqNoGrid>        <!-- = SeqNo -->
<IsDisplayedGrid>Y</IsDisplayedGrid>
```

**這個 bug 在官方文件裡完全沒有提到。** 唯一的線索是 Stack Trace 裡的 `Index: 2`，而且必須對 iDempiere 原始碼有一定了解才能從那裡追回來。

---

### 坑 5：兩個 JVM 搶 port，deploy 進錯的那個

這個花了最多時間診斷。症狀是：

- OSGi telnet console：bundle ACTIVE ✅
- Web Console（https://.../ osgi/system/console/bundles）：找不到這個 bundle ❌

最後發現機器上跑著**兩個** iDempiere JVM：

| JVM | 擁有 port | 說明 |
|-----|-----------|------|
| JVM-A（舊的）| 8080/8443 | Web 瀏覽器連這個 |
| JVM-B（新的）| 12612 | OSGi telnet 連這個 |

`deploy.sh` 透過 telnet 把 bundle 裝進了 JVM-B，但使用者看的 Web Console 是 JVM-A——所以當然看不到。

原因是 `idempiere-server.sh` 有 restart loop，`systemctl restart` 啟動了新 JVM，但舊 JVM 沒死，兩個同時搶 port 各拿一半。

診斷方法：
```bash
ps aux | grep java | grep -v grep | wc -l  # > 1 就是這個問題
```

修復：kill 兩個 JVM，等 30 秒讓 port 釋放，再 restart 一次。

---

### 坑 6：AD_Field UUID 衝突

2Pack 重裝時，iDempiere 已為新建的 Tab 自動插入標準欄位（AD_Client_ID、AD_Org_ID、IsActive）。當 PackIn 再次嘗試用不同 UUID 插入同一個 `(tab_id, column_id)` 組合時，違反了 `UNIQUE(ad_tab_id, ad_column_id)` 約束，拋出 `POSaveFailedException`。

解法：對這類「系統可能已存在」的標準欄位，PackOut.xml 使用**固定的 placeholder UUID**。PackIn 遇到重複就 UPDATE 而不是 INSERT，不再衝突。

**升版 UUID 策略**：
- 已存在的 field → 保留原 UUID
- 新增的 field → 才用新的 uuid4
- 不要為了「整齊」換掉既有 UUID，會導致插入失敗

---

### 坑 7：清除 DB 做全新安裝——FK 順序

當我們想做「完整卸載 → 清 DB → 重裝」的驗證時，刪資料的順序錯了好幾次。

`AD_Field` → `AD_Tab` → `AD_Window`：每一步都有 FK 指向下一個，刪之前要先清乾淨子表。尤其是 `AD_Preference` 和 `AD_Menu` 也會 FK 到 `AD_Window`，忘記刪這兩個就卡住。

還有一個陷阱：`ad_package_imp` 記錄了 2Pack 安裝歷史，不清掉的話 `Incremental2PackActivator` 判定「已安裝過同版本」就跳過不執行。

另外：多條 SQL 放在同一個 `psql -c "..." ` 裡，任何一條失敗整批 rollback——每條要分開執行。

---

## 如果重來一次，我們會怎麼做

### 1. 先建立 PackOut.xml 驗證腳本

每次 2Pack 安裝後自動跑這些 SQL：
```sql
-- SeqNoGrid 是否設定
SELECT tablename, count(*) FILTER (WHERE seqnogrid > 0) ok
FROM ad_table JOIN ad_tab ... JOIN ad_field ...
WHERE tablename LIKE 'TW_%' GROUP BY tablename;

-- _UU 欄位是否 updateable
SELECT columnname, isupdateable FROM ad_column
WHERE tablename LIKE 'TW_%' AND columnname LIKE '%_UU';
```

出問題立刻知道，不用等到 UI 爆炸。

### 2. 把 ZIP 結構驗證寫進 Maven build

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <executions>
    <execution>
      <phase>verify</phase>
      <goals><goal>exec</goal></goals>
      <configuration>
        <executable>bash</executable>
        <arguments>
          <argument>-c</argument>
          <argument>unzip -l resources/META-INF/2Pack_*.zip | grep -q "dict/PackOut.xml"</argument>
        </arguments>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### 3. EventHandler 整合測試

目前 EventHandler 的驗證邏輯透過靜態 Validator 方法的單元測試覆蓋，但 EventHandler 本身（OSGi 事件 → 呼叫 Validator → 拋 AdempiereException）沒有整合測試。下一步要加 mock 框架測試完整的事件處理流程。

### 4. 每次 commit 前跑 `deploy.sh --check`

加一個 dry-run 模式，只驗證 JAR 可部署、OSGi console 可連線、bundle 存在，不實際更新。讓這個檢查成為 commit hook。

---

## 最終結果

```
Bundle:    tw.idempiere.invoice.tax v1.0.0 (2Pack v1.0.10)
Tests:     87 個，全數通過
Tables:    4 張 TW_* 資料表
Windows:   4 個 iDempiere 視窗
Fields:    73 個 AD_Field（含正確 SeqNoGrid）
Status:    ACTIVE，Grid View 正常，新增/儲存/查詢驗證通過
Release:   github.com/tm731531/idempiere-tw-invoice-system/releases/tag/v1.0.10
```

---

## 給下一個要做 iDempiere Plugin 的人

iDempiere 的文件很少，而且很多行為只有讀原始碼才能理解。幾個花最多時間搞懂的事：

1. **`ModelValidator` 不是 OSGi 服務**，不要以為 `@Component` 就能讓它跑起來
2. **`SeqNoGrid` 是 Grid View 的必備欄位**，缺了不報錯，只在你按「新增」時崩
3. **2Pack ZIP 結構有嚴格要求**，打包後一定要 `unzip -l` 驗證
4. **`_UU` 欄位必須 `IsUpdateable=Y`**，否則 UUID 永遠 NULL
5. **`IEventTopics` 常數**，不要用字串字面量比較 topic
6. **Incremental2PackActivator 會記住安裝歷史**，同版本不重裝，升版要改 ZIP 檔名

希望這篇文章讓你少踩一些坑。

---

*作者：TomTing｜2026-03-27*
*GitHub：[tm731531/idempiere-tw-invoice-system](https://github.com/tm731531/idempiere-tw-invoice-system)*
