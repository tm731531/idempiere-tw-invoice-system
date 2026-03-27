# 開發反省與教學手冊 — 2026-03-27

> 本文件記錄台灣統一發票系統（`tw.idempiere.invoice.tax`）這次開發週期中遭遇的所有問題、根本原因、修復方式，以及下次應該怎麼做。
> 每個問題都附上「為什麼會犯」和「怎麼不再犯」，目的是讓這些痛苦的教訓變成可複用的知識。

---

## 目錄

1. [Bug 清單與根本原因](#1-bug-清單與根本原因)
2. [2Pack / PackOut.xml 陷阱大全](#2-2pack--packoutxml-陷阱大全)
3. [OSGi 部署陷阱](#3-osgi-部署陷阱)
4. [Git 操作失誤](#4-git-操作失誤)
5. [iDempiere 框架規則（血淚版）](#5-idempiere-框架規則血淚版)
6. [正確工作流程 SOP](#6-正確工作流程-sop)
7. [下次開發前必讀清單](#7-下次開發前必讀清單)

---

## 1. Bug 清單與根本原因

### Bug #1：事件主題用字串後綴比較（Critical）

**症狀**：PO_BEFORE_CHANGE 事件從不觸發，`InvoicePrefixEventHandler` 狀態轉換守衛靜默失效。

**錯誤寫法：**
```java
if (topic.endsWith("po_before_change")) { ... }
```

**正確寫法：**
```java
if (IEventTopics.PO_BEFORE_CHANGE.equals(topic)) { ... }
```

**為什麼會犯**：複製其他範例時沒有追查常數定義，用字串字面量比較看起來沒問題但實際上 iDempiere 的 topic 格式是 `org/adempiere/po/PO_BEFORE_CHANGE`，endsWith 比較的是小寫後綴，而實際 topic 中可能是大寫或有路徑前綴。

**防範方式**：事件主題**永遠用常數**，`IEventTopics.PO_BEFORE_CHANGE`、`IEventTopics.PO_BEFORE_NEW`，不要用字串字面量。

---

### Bug #2：A→I 狀態轉換未攔截（Critical）

**症狀**：使用者可以把「使用中（A）」的字軌降回「未啟用（I）」，違反台灣稅法。

**根本原因**：驗證邏輯只攔截了 `C → 任何狀態`，忘記攔截 `A → I`。

**修復：**
```java
// A→I is forbidden: active prefix cannot be deactivated
if ("A".equals(oldStatus) && "I".equals(newStatus)) return "使用中字軌不可降回未啟用...";
```

**防範方式**：每次寫狀態機驗證，先畫出完整的「禁止轉換矩陣」，而不是只想到「終態」。

---

### Bug #3：ModelValidator 實作從未被呼叫（Dead Code，Critical）

**症狀**：`InvoicePrefixValidator` 實作了 `ModelValidator` 介面的所有方法，但驗證從不執行。

**根本原因**：iDempiere 的 `ModelValidator` 需要透過 `org.compiere.model.ModelValidationEngine` 動態載入，不是 OSGi DS 服務，不是 `@Component` 就能自動啟動的。這個 plugin 沒有任何地方把 validator 登錄進去。

**正確架構**：
```
*Validator.java    ← 純靜態方法，不實作任何介面
*EventHandler.java ← extends AbstractEventHandler，透過 OSGI-INF/*.xml 登錄為 DS 服務
```

**防範方式**：除非你明確了解 `ModelValidationEngine.addModelValidator()` 的呼叫位置，否則**不要讓 validator 類別實作 ModelValidator 介面**。iDempiere plugin 的標準驗證模式是 EventHandler + 靜態 Validator，不是 ModelValidator。

---

### Bug #4：PackOut.xml 完全缺少 SeqNoGrid（Critical）

**症狀**：四個 TW 視窗按「新增」就崩潰，拋出 `IndexOutOfBoundsException: Index: 2`。

**根本原因**：`PackOut.xml` 的所有 73 個 `<AD_Field>` 元素均未設定 `<SeqNoGrid>`。iDempiere 12 的 Grid View 渲染器 (`GridTabRowRenderer.editCurrentRow`) 在初始化行編輯器時讀取 `SeqNoGrid`，全部為 NULL 導致內部列表狀態不一致，`list.get(2)` 越界。

**修復**：設定 `SeqNoGrid = SeqNo`（顯示欄位），`SeqNoGrid = 0` + `IsDisplayedGrid = N`（隱藏欄位）。

**正確的 AD_Field 完整元素**（每個 `<AD_Field>` 都要有）：
```xml
<SeqNo>10</SeqNo>
<SeqNoGrid>10</SeqNoGrid>
<IsDisplayedGrid>Y</IsDisplayedGrid>
<IsDisplayed>Y</IsDisplayed>
```
隱藏欄位（如 PK、_UU、Created、CreatedBy...）：
```xml
<SeqNo>0</SeqNo>
<SeqNoGrid>0</SeqNoGrid>
<IsDisplayedGrid>N</IsDisplayedGrid>
<IsDisplayed>N</IsDisplayed>
```

**防範方式**：把 `<SeqNoGrid>` 和 `<IsDisplayedGrid>` 加入 PackOut.xml 的 AD_Field 必備欄位清單。每次從標準表格（如 `C_Tax`）匯出 PackOut.xml 時，確認這兩個欄位有出現。

---

### Bug #5：2Pack ZIP 打包方式錯誤

**症狀**：`Pack in failed. File does not exist: /tmp/PackOut.xml/dict/PackOut.xml`

**錯誤指令**：
```bash
zip -j 2Pack_1.0.10.zip PackOut.xml   # -j 會剝掉目錄結構
```

**正確結構**（ZIP 內必須有此路徑）：
```
tw_invoice_system/
└── dict/
    └── PackOut.xml
```

**正確打包方式**：
```bash
mkdir -p /tmp/build/tw_invoice_system/dict
cp PackOut.xml /tmp/build/tw_invoice_system/dict/
cd /tmp/build
zip -r /path/to/2Pack_1.0.10.zip tw_invoice_system/
```

**為什麼會犯**：複製舊指令時沒有驗證 ZIP 結構，`-j` 是「junk paths」，這個錯誤在第一次做這個 plugin 時就犯過一次（1.0.8 版），這次又重犯。

**防範方式**：打包後一定要執行 `unzip -l 2Pack_xxx.zip` 確認目錄結構。把這個驗證步驟寫進 build script。

---

### Bug #6：雙 JVM 搶 port（部署環境問題）

**症狀**：OSGi telnet 顯示 bundle ACTIVE，但 Web Console 看不到這個 bundle。

**根本原因**：`idempiere-server.sh` 有 restart loop，舊 JVM 還在跑時新的 JVM 也啟動了。結果：
- JVM-A（PID 舊的）：持有 port 8080/8443（Web Console 連這個）
- JVM-B（PID 新的）：持有 port 12612（telnet 連這個）

我們透過 telnet 把 bundle 裝進了 JVM-B，但 Web Console 連的是 JVM-A，所以 JVM-A 看不到 bundle。

**診斷方式**：
```bash
ss -tlnp | grep "8080\|8443\|12612"
# 找出各 port 的 inode，再比對 /proc/<pid>/fd 確認是哪個 JVM 擁有哪個 port
```

**修復**：`sudo systemctl restart idempiere` 一次，等舊 JVM 完全死掉，再確認只剩一個 Java 進程，最後等 8080 port 出現。

**防範方式**：每次部署前先確認 `ps aux | grep java` 只有一個 JVM。如果有兩個，先重啟再部署。

---

## 2. 2Pack / PackOut.xml 陷阱大全

### AD_Field 必備欄位清單

每個 `<AD_Field>` 至少要有這些欄位，**缺一就可能造成執行期崩潰或 UI 異常**：

| 欄位 | 說明 | 隱藏欄位 | 顯示欄位 |
|------|------|---------|---------|
| `<SeqNo>` | Form view 排序 | `0` | `10, 20, 30...` |
| `<SeqNoGrid>` | Grid view 排序，**v1.0.10 前完全缺失** | `0` | 同 SeqNo |
| `<IsDisplayed>` | 是否在 Form view 顯示 | `N` | `Y` |
| `<IsDisplayedGrid>` | 是否在 Grid view 顯示 | `N` | `Y`（關鍵欄位）或 `N`（次要欄位） |
| `<IsReadOnly>` | 是否唯讀 | — | 視需求 |
| `<IsMandatory>` | 是否必填 | — | 視業務規則 |

### 驗證 2Pack 安裝的 SQL

安裝後立刻用這些 SQL 確認：
```sql
-- 確認 SeqNoGrid 已設定
SELECT tablename, count(*) FILTER (WHERE f.seqnogrid > 0) as ok,
       count(*) FILTER (WHERE f.seqnogrid IS NULL OR f.seqnogrid = 0) as missing
FROM ad_table t
JOIN ad_tab tab ON tab.ad_table_id = t.ad_table_id
JOIN ad_field f ON f.ad_tab_id = tab.ad_tab_id
WHERE t.tablename LIKE 'TW_%'
GROUP BY tablename;

-- 確認 IsDisplayedGrid 正確
SELECT tablename,
       count(*) FILTER (WHERE f.isdisplayed='Y' AND f.isdisplayedgrid='N') as displayed_but_hidden_in_grid
FROM ad_table t
JOIN ad_tab tab ON tab.ad_table_id = t.ad_table_id
JOIN ad_field f ON f.ad_tab_id = tab.ad_tab_id
WHERE t.tablename LIKE 'TW_%'
GROUP BY tablename;
```

### ZIP 結構驗證

```bash
# 打包後一定要跑這一行
unzip -l resources/META-INF/2Pack_*.zip
# 期望看到：
# tw_invoice_system/
# tw_invoice_system/dict/
# tw_invoice_system/dict/PackOut.xml
```

---

## 3. OSGi 部署陷阱

### 熱部署流程

```bash
./deploy.sh   # 會自動 build → copy JAR → telnet OSGi console → update bundle
```

**部署前確認**：
```bash
ps aux | grep java | grep -v grep | wc -l   # 必須是 1
ss -tlnp | grep 8080                         # 必須有值（Jetty 在跑）
```

### 確認 bundle 狀態

```bash
# 透過 telnet 確認
python3 -c "
import socket,time,re,sys
s=socket.socket(); s.connect(('127.0.0.1',12612)); s.settimeout(2)
try:
    while True: s.recv(4096)
except: pass
s.sendall(b'\xff\xfd\x01\xff\xfd\x03')
time.sleep(0.5)
try:
    while True: s.recv(4096)
except: pass
s.sendall(b'ss tw.idempiere.invoice.tax\r\n')
time.sleep(3)
data=b''
try:
    while True: data+=s.recv(4096)
except: pass
print(data.decode('utf-8','replace'))
"
```

期望結果：`694  ACTIVE  tw.idempiere.invoice.tax_1.0.0`

### 確認 2Pack 安裝成功

```bash
tail -5 $(ls -t /opt/idempiere-server/x86_64/log/idempiere.*.log | head -1)
# 期望看到：
# Incremental2PackActivator.packIn: tw.idempiere.invoice.tax /META-INF/2Pack_1.0.10.zip installed [xxx]
```

如果看到 `Pack in failed`，立刻查：
```bash
grep -A20 "Pack in failed" /opt/idempiere-server/x86_64/log/idempiere.*.log | tail -25
```

### 部署後必做：清除 metadata 緩存

2Pack 更新了 AD_Field/AD_Tab/AD_Window，但 iDempiere 有 dictionary 緩存，不清除的話 UI 不會反映新的設定。

**選一個方法：**
1. iDempiere UI 左側選單 → **Cache Reset**
2. 或**登出再登入**

---

## 4. Git 操作失誤

### 失誤：直接 push 到 main

**發生經過**：在做完修復後直接執行 `git push origin main`，忘記目前要在 dev 分支工作。

**鐵則**（已寫入 memory）：
- `dev` = 日常工作分支
- `main` = 穩定生產版，只有 dev 穩定後才 merge 進去
- **永遠不直接 push main**

**補救方式**：
```bash
# 把 main 上的 commit cherry-pick 到 dev
git checkout dev
git cherry-pick <sha1> <sha2>
git push origin dev
# main 不動（讓它停在上一個穩定版）
```

**防範方式**：每次 push 前確認：
```bash
git branch   # 確認你在 dev 或 feature/*
git push origin dev   # 明確指定，不用 git push
```

### 正確的分支策略

```
main          ← 穩定版，打 tag，發 Release
  └─ dev      ← 日常工作，功能完成後 merge 回 main
       └─ feature/xxx  ← 特定功能，完成後 merge 回 dev，刪除分支
```

---

## 5. iDempiere 框架規則（血淚版）

### ModelValidator vs EventHandler

| | ModelValidator | EventHandler (AbstractEventHandler) |
|--|--|--|
| 登錄方式 | `ModelValidationEngine.addModelValidator()` | OSGI-INF/*.xml DS component |
| 適用場景 | 需要監聽多個表格、需要 login 事件 | 監聽特定表格的 PO_BEFORE_NEW/CHANGE |
| 本 plugin 使用 | **不用** | **使用** |

**結論**：本 plugin 不使用 ModelValidator，驗證類別只含靜態方法，EventHandler 呼叫這些靜態方法。

### PackIn 的 XML 格式要求

```xml
<!-- AD_Field 順序（這個順序在 PackOut.xml 的 DTD 中有規定）-->
<AD_Field type="table">
  <AD_Client_ID>...</AD_Client_ID>
  <AD_Org_ID>...</AD_Org_ID>
  <Name>...</Name>
  <EntityType>TW</EntityType>
  <SeqNo>10</SeqNo>
  <SeqNoGrid>10</SeqNoGrid>      ← 不能省略！
  <IsDisplayedGrid>Y</IsDisplayedGrid>  ← 不能省略！
  <IsDisplayed>Y</IsDisplayed>
  <IsReadOnly>N</IsReadOnly>
  <IsMandatory>N</IsMandatory>
  <IsSameLine>N</IsSameLine>
  <IsHeading>N</IsHeading>
  <IsFieldOnly>N</IsFieldOnly>
  <IsEncrypted>N</IsEncrypted>
  <IsCentrallyMaintained>Y</IsCentrallyMaintained>
  <AD_Tab_ID reference="uuid" ...>...</AD_Tab_ID>
  <AD_Column_ID reference="uuid" ...>...</AD_Column_ID>
  <AD_Field_UU>xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx</AD_Field_UU>
</AD_Field>
```

### _UU 欄位必須 IsUpdateable=Y

iDempiere PO 框架在儲存新記錄時會自動寫入 `_UU` 欄位的 UUID 值。如果 `IsUpdateable=N`，這個欄位永遠是 NULL。

```xml
<IsUpdateable>Y</IsUpdateable>  ← _UU 欄位必須如此
```

參考標準表格：`C_Tax`、`C_BPartner` 的 `_UU` 欄位都是 `IsUpdateable=Y`。

### AD_Field UUID 升版策略

`AD_Field` 有 `UNIQUE(ad_tab_id, ad_column_id)` 約束。升版 2Pack 時：
- **已存在的 field**：保留原本的 UUID，不要改
- **新增的 field**：才用新的 uuid4

改 UUID 會導致 INSERT 失敗（unique constraint violation）。

---

## 6. 正確工作流程 SOP

### 新功能開發 SOP

```
1. git checkout dev && git pull origin dev
2. git checkout -b feature/my-feature

3. 開發、測試
   - mvn test 全過才繼續
   - 每個新 AD_Field 確認有 SeqNoGrid
   - 打包 2Pack ZIP 後 unzip -l 確認目錄結構

4. git commit（在 feature 分支）
5. git checkout dev && git merge --no-ff feature/my-feature
6. git push origin dev
7. git branch -d feature/my-feature && git push origin --delete feature/my-feature
```

### 2Pack 版本升版 SOP

```
1. 修改 resources/2pack/tw_invoice_system/dict/PackOut.xml
2. 決定新版本號（如 1.0.11）
3. 重新打包 ZIP（注意目錄結構！）
   mkdir -p /tmp/build/tw_invoice_system/dict
   cp PackOut.xml /tmp/build/tw_invoice_system/dict/
   cd /tmp/build && zip -r /path/2Pack_1.0.11.zip tw_invoice_system/
   unzip -l /path/2Pack_1.0.11.zip  ← 驗證！
4. 刪除舊 ZIP，放入新 ZIP
5. 更新 test/ActivatorPackInTest.java 的 PACK_VERSION 常數
6. mvn clean package（確認測試通過）
7. ./deploy.sh
8. 確認 log："...installed" 而非 "Pack in failed"
9. 執行 SQL 驗證 DB 狀態
10. 清 Cache（Cache Reset 或重新登入）
11. 手動測試 UI（每個視窗試「新增」）
```

### Release SOP

```
1. 確認 dev 上所有測試通過、文件更新
2. git tag -a v1.0.x -m "Release v1.0.x — 說明"
3. git push origin dev && git push origin v1.0.x
4. gh release create v1.0.x target/*.jar --title "..." --notes "..."
5. (可選) 更新 main: git checkout main && git merge dev && git push origin main
```

---

## 7. 下次開發前必讀清單

在開始任何 PackOut.xml 修改前，確認這些：

- [ ] 每個 `<AD_Field>` 都有 `<SeqNoGrid>` 和 `<IsDisplayedGrid>`
- [ ] 2Pack ZIP 用 `unzip -l` 確認有 `tw_invoice_system/dict/PackOut.xml` 路徑
- [ ] 事件主題比較用 `IEventTopics.PO_BEFORE_NEW.equals(topic)`，不用字串
- [ ] Validator 類別**不實作 ModelValidator**，只有靜態方法
- [ ] 每個 EventHandler 在 `OSGI-INF/` 下有對應的 `.xml` 元件描述檔
- [ ] `ps aux | grep java` 只有一個 JVM
- [ ] 確認在 `dev` 或 `feature/*` 分支，不在 `main`
- [ ] 部署後執行 SQL 驗證 `seqnogrid`、`isdisplayedgrid` 值
- [ ] 手動測試四個視窗的「新增」、「儲存」、「查詢」功能
