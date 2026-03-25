# PackOut.xml Column Reference

> 撰寫 PackOut.xml 的 `<AD_Column>` 時，對照此表選擇正確的 `<AD_Reference_ID>`。
> 這是 pack-builder agent 的參考文件。

---

## AD_Reference_ID 對照表

| ID | 類型 | Java 型別 | 說明 |
|----|------|----------|------|
| 10 | String | String | 文字（FieldLength = max chars） |
| 11 | Integer | int / BigDecimal | 整數 |
| 12 | Amount | BigDecimal | 金額（通常 15,2） |
| 13 | ID | int | Primary Key（PK 欄位專用） |
| 15 | Date | Timestamp | 日期（FieldLength=7） |
| 16 | DateTime | Timestamp | 日期時間（FieldLength=7） |
| 17 | List | String | 參考清單（需搭配 AD_Reference_Value_ID） |
| 19 | TableDir | int | FK 到另一張表（欄位名稱即表名，如 AD_Client_ID） |
| 20 | YesNo | boolean | Y/N 旗標（FieldLength=1） |
| 22 | Number | BigDecimal | 浮點數 |
| 29 | Quantity | BigDecimal | 數量 |
| 30 | Search | int | FK 搜尋欄位（搭配 AD_Reference_Value_ID 指定來源表） |
| 36 | CHAR | String | 固定長度字元 |
| 110 | AD_User | int | FK 到 AD_User（CreatedBy、UpdatedBy 專用） |

---

## 標準欄位模板

每張 TW_* 表的 `<AD_Column>` 必須包含以下標準欄位（依此順序）：

```xml
<!-- 1. Primary Key -->
<AD_Column>
  <ColumnName>TW_Xxx_ID</ColumnName>
  <Name>TW Xxx</Name>
  <AD_Reference_ID reference="id" reference-key="AD_Reference">13</AD_Reference_ID>
  <FieldLength>10</FieldLength>
  <IsMandatory>Y</IsMandatory>
  <IsKey>Y</IsKey>
  <IsActive>Y</IsActive>
  <EntityType>TW</EntityType>
  <AD_Column_UU>STABLE-UUID-HERE</AD_Column_UU>
</AD_Column>

<!-- 2. UUID -->
<AD_Column>
  <ColumnName>TW_Xxx_UU</ColumnName>
  <Name>TW_Xxx_UU</Name>
  <AD_Reference_ID reference="id" reference-key="AD_Reference">10</AD_Reference_ID>
  <FieldLength>36</FieldLength>
  <IsMandatory>N</IsMandatory>
  <IsKey>N</IsKey>
  <IsActive>Y</IsActive>
  <EntityType>TW</EntityType>
  <AD_Column_UU>STABLE-UUID-HERE</AD_Column_UU>
</AD_Column>

<!-- 3. AD_Client_ID -->
<AD_Column>
  <ColumnName>AD_Client_ID</ColumnName>
  <Name>Client</Name>
  <AD_Reference_ID reference="id" reference-key="AD_Reference">19</AD_Reference_ID>
  <FieldLength>10</FieldLength>
  <IsMandatory>Y</IsMandatory>
  <IsKey>N</IsKey>
  <IsActive>Y</IsActive>
  <EntityType>TW</EntityType>
  <AD_Column_UU>STABLE-UUID-HERE</AD_Column_UU>
</AD_Column>

<!-- 4. AD_Org_ID -->
<AD_Column>
  <ColumnName>AD_Org_ID</ColumnName>
  <Name>Organization</Name>
  <AD_Reference_ID reference="id" reference-key="AD_Reference">19</AD_Reference_ID>
  <FieldLength>10</FieldLength>
  <IsMandatory>Y</IsMandatory>
  <IsKey>N</IsKey>
  <IsActive>Y</IsActive>
  <EntityType>TW</EntityType>
  <AD_Column_UU>STABLE-UUID-HERE</AD_Column_UU>
</AD_Column>

<!-- 5–9. Audit columns (IsActive, Created, CreatedBy, Updated, UpdatedBy) -->
<AD_Column>
  <ColumnName>IsActive</ColumnName>
  <Name>Active</Name>
  <AD_Reference_ID reference="id" reference-key="AD_Reference">20</AD_Reference_ID>
  <FieldLength>1</FieldLength>
  <IsMandatory>Y</IsMandatory>
  <IsKey>N</IsKey>
  <DefaultValue>Y</DefaultValue>
  <IsActive>Y</IsActive>
  <EntityType>TW</EntityType>
  <AD_Column_UU>STABLE-UUID-HERE</AD_Column_UU>
</AD_Column>

<AD_Column>
  <ColumnName>Created</ColumnName>
  <Name>Created</Name>
  <AD_Reference_ID reference="id" reference-key="AD_Reference">16</AD_Reference_ID>
  <FieldLength>7</FieldLength>
  <IsMandatory>Y</IsMandatory>
  <IsKey>N</IsKey>
  <IsActive>Y</IsActive>
  <EntityType>TW</EntityType>
  <AD_Column_UU>STABLE-UUID-HERE</AD_Column_UU>
</AD_Column>

<AD_Column>
  <ColumnName>CreatedBy</ColumnName>
  <Name>Created By</Name>
  <AD_Reference_ID reference="id" reference-key="AD_Reference">110</AD_Reference_ID>
  <FieldLength>10</FieldLength>
  <IsMandatory>Y</IsMandatory>
  <IsKey>N</IsKey>
  <IsActive>Y</IsActive>
  <EntityType>TW</EntityType>
  <AD_Column_UU>STABLE-UUID-HERE</AD_Column_UU>
</AD_Column>

<AD_Column>
  <ColumnName>Updated</ColumnName>
  <Name>Updated</Name>
  <AD_Reference_ID reference="id" reference-key="AD_Reference">16</AD_Reference_ID>
  <FieldLength>7</FieldLength>
  <IsMandatory>Y</IsMandatory>
  <IsKey>N</IsKey>
  <IsActive>Y</IsActive>
  <EntityType>TW</EntityType>
  <AD_Column_UU>STABLE-UUID-HERE</AD_Column_UU>
</AD_Column>

<AD_Column>
  <ColumnName>UpdatedBy</ColumnName>
  <Name>Updated By</Name>
  <AD_Reference_ID reference="id" reference-key="AD_Reference">110</AD_Reference_ID>
  <FieldLength>10</FieldLength>
  <IsMandatory>Y</IsMandatory>
  <IsKey>N</IsKey>
  <IsActive>Y</IsActive>
  <EntityType>TW</EntityType>
  <AD_Column_UU>STABLE-UUID-HERE</AD_Column_UU>
</AD_Column>
```

---

## List 類型欄位模板（AD_Reference_ID=17）

```xml
<AD_Column>
  <ColumnName>InvoiceType</ColumnName>
  <Name>發票類型</Name>
  <AD_Reference_ID reference="id" reference-key="AD_Reference">17</AD_Reference_ID>
  <AD_Reference_Value_ID reference="uuid" reference-key="AD_Reference">UUID-OF-TW_INVOICETYPE-REFERENCE</AD_Reference_Value_ID>
  <FieldLength>20</FieldLength>
  <IsMandatory>Y</IsMandatory>
  <IsKey>N</IsKey>
  <IsActive>Y</IsActive>
  <EntityType>TW</EntityType>
  <AD_Column_UU>STABLE-UUID-HERE</AD_Column_UU>
</AD_Column>
```

---

## FK 欄位模板（AD_Reference_ID=19 TableDir）

```xml
<!-- TableDir: 欄位名稱必須是 TableName_ID 格式，iDempiere 自動解析目標表 -->
<AD_Column>
  <ColumnName>TW_InvoicePrefix_ID</ColumnName>
  <Name>字軌</Name>
  <AD_Reference_ID reference="id" reference-key="AD_Reference">19</AD_Reference_ID>
  <FieldLength>10</FieldLength>
  <IsMandatory>Y</IsMandatory>
  <IsKey>N</IsKey>
  <IsActive>Y</IsActive>
  <EntityType>TW</EntityType>
  <AD_Column_UU>STABLE-UUID-HERE</AD_Column_UU>
</AD_Column>
```

---

## AD_Sequence 模板（每張 PK 表必須有）

```xml
<AD_Sequence>
  <Name>TW_InvoicePrefix</Name>
  <Description>Taiwan Invoice Prefix sequence</Description>
  <VFormat></VFormat>
  <IncrementNo>1</IncrementNo>
  <StartNewYear>N</StartNewYear>
  <StartNo>1000000</StartNo>
  <CurrentNextSys>1000000</CurrentNextSys>
  <CurrentNext>1000000</CurrentNext>
  <IsTableID>Y</IsTableID>
  <IsActive>Y</IsActive>
  <AD_Sequence_UU>STABLE-UUID-HERE</AD_Sequence_UU>
</AD_Sequence>
```

> 每張 TW_* 表都需要一個對應的 `<AD_Sequence>`，放在 PackOut.xml 的 AD_Table 定義之前。
