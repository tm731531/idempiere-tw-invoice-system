# E-Invoice Transmission (F0401 B2C 存證開立 first cut) Implementation Plan

> **CORRECTION (2026-10-13, authoritative):** MIG 4.1 names the B2C 存證開立 message **`F0401`** (namespace `urn:GEINV:eInvoiceMessage:F0401:4.1`), NOT `C0401` (that was MIG 3.2). All `C0401` references below are superseded by `F0401`/4.1. Gate = reproduce the official sample `test/resources/mig/F0401-B2C-print-sample.xml` (no XSD in the sample package).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Before writing code**, invoke the Domain Skills declared in this repo's `CLAUDE.md` (`idempiere-mapped-model-factory-service`, `idempiere-annotation-process`) and keep the iDempiere brain rules in mind.

**Goal:** Turn one issued B2C taxable (列印紙本) invoice into an F0401 (MIG 4.1) XML file matching the official sample, dropped into Turnkey's intake directory, recorded in a new `TW_EInvoiceMessage` table.

**Architecture:** A new pure-Java `einvoice` sub-package assembles invoice data into a canonical `EInvoiceDoc`, builds F0401 XML (`MigF0401Builder`), and hands it to a pluggable `TransmissionAdapter` (`TurnkeyFileAdapter` = file drop). An iDempiere `SvrProcess` sources the data from existing models and persists a transmission record. Pure pieces are JUnit-tested offline; XML correctness is gated by reproducing the official F0401 sample (byte-structure match after whitespace normalization).

**Tech Stack:** iDempiere 12 OSGi plugin (`tw.idempiere.invoice.tax`), Java, JUnit 4, Maven, 2Pack (PackOut.xml), `javax.xml.validation` + `xmllint` for XSD validation.

## Global Constraints

- Bundle: `tw.idempiere.invoice.tax`; new code under package `tw.idempiere.invoice.tax.einvoice`.
- Monetary math: **`BigDecimal` + `RoundingMode.FLOOR` only** — never `float`/`double`, never `ROUND`. Reuse existing `TaxCalculationService` for amounts.
- Every new iDempiere model class: `@org.adempiere.base.Model(table = ...)`; `initPO()` uses `MTable.getTable_ID(Table_Name)` with `<= 0` guard. Never hardcode `Table_ID`/reference IDs.
- `TaiwanModelFactory` already scans package `tw.idempiere.invoice.tax.model` — new models go there to be auto-discovered.
- Every `SvrProcess` MUST be registered in `TaiwanProcessFactory.newProcessInstance(...)`.
- 2Pack: bump version (`2Pack_1.0.11.zip` → `2Pack_1.0.12.zip` + `PackOut.xml Version=`); keep existing UUIDs; every grid field needs `SeqNoGrid`+`IsDisplayedGrid=Y`; standard AD_Field set per tab; `_UU` `IsUpdateable=Y`.
- **The official sample `test/resources/mig/F0401-B2C-print-sample.xml` is the authoritative source for element names, namespace (`urn:GEINV:eInvoiceMessage:F0401:4.1`), and ordering.** The builder must reproduce it. If an `F0401.xsd` is obtained later, add XSD validation as a second gate.
- Language: all code, comments, commits in English. Git stays on `dev`. JUnit 4 (`import org.junit.Test; import static org.junit.Assert.*;`).

---

### Task 1: Acquire official MIG 4.x C0401 XSD + sample

**Files:**
- Create: `test/resources/mig/C0401.xsd`
- Create: `test/resources/mig/C0401-sample.xml`

**Interfaces:**
- Produces: the authoritative XSD + a known-valid sample, consumed by Task 4's validation test.

- [ ] **Step 1: Download the official MIG 4.x package**

From the Ministry of Finance e-invoice platform download area (`https://www.einvoice.nat.gov.tw` → 下載專區 → 電子發票資料交換標準訊息建置指引 (MIG) 4.x → 資料交換標準文件與範例). Extract `C0401.xsd` and its companion schemas (it `include`s shared types — grab the whole `C0401` schema set) and the official `C0401` sample XML.

- [ ] **Step 2: Place files and self-validate the sample against the XSD**

Run:
```bash
xmllint --noout --schema test/resources/mig/C0401.xsd test/resources/mig/C0401-sample.xml && echo "SAMPLE VALID"
```
Expected: `SAMPLE VALID`. If it fails, the XSD set is incomplete (missing an `include`d schema) — fetch the rest before proceeding.

- [ ] **Step 3: Record the real namespace**

Open `C0401-sample.xml`, copy the exact root `xmlns` (e.g. `urn:GEINV:eInvoiceMessage:C0401:4.0`) into a note at the top of `test/resources/mig/README.md`. Tasks 4 uses this verbatim.

- [ ] **Step 4: Commit**

```bash
git add test/resources/mig/
git commit -m "test: add official MIG 4.x C0401 XSD and sample as validation fixtures"
```

---

### Task 2: RandomNumberGenerator

**Files:**
- Create: `src/tw/idempiere/invoice/tax/einvoice/RandomNumberGenerator.java`
- Test: `test/tw/idempiere/invoice/tax/einvoice/RandomNumberGeneratorTest.java`

**Interfaces:**
- Produces: `String RandomNumberGenerator.generate(IntSupplier source)` → 4-digit zero-padded string; `String RandomNumberGenerator.generate()` → uses a default random source.

- [ ] **Step 1: Write the failing test**

```java
package tw.idempiere.invoice.tax.einvoice;

import static org.junit.Assert.*;
import org.junit.Test;

public class RandomNumberGeneratorTest {

    @Test
    public void zeroPadsToFourDigits() {
        assertEquals("0007", RandomNumberGenerator.generate(() -> 7));
    }

    @Test
    public void takesModulo10000() {
        assertEquals("2345", RandomNumberGenerator.generate(() -> 12345));
    }

    @Test
    public void defaultGenerateIsFourDigits() {
        String r = RandomNumberGenerator.generate();
        assertTrue(r.matches("\\d{4}"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=RandomNumberGeneratorTest`
Expected: FAIL — class/method not defined.

- [ ] **Step 3: Write minimal implementation**

```java
package tw.idempiere.invoice.tax.einvoice;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/** Generates the 4-digit 隨機碼 required by MIG C0401. */
public final class RandomNumberGenerator {

    private RandomNumberGenerator() {}

    public static String generate(IntSupplier source) {
        int n = Math.floorMod(source.getAsInt(), 10000);
        return String.format("%04d", n);
    }

    public static String generate() {
        return generate(() -> ThreadLocalRandom.current().nextInt(10000));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=RandomNumberGeneratorTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/tw/idempiere/invoice/tax/einvoice/RandomNumberGenerator.java test/tw/idempiere/invoice/tax/einvoice/RandomNumberGeneratorTest.java
git commit -m "feat: add 4-digit RandomNumber generator for C0401"
```

---

### Task 3: Canonical EInvoiceDoc + assembler

**Files:**
- Create: `src/tw/idempiere/invoice/tax/einvoice/EInvoiceItem.java`
- Create: `src/tw/idempiere/invoice/tax/einvoice/EInvoiceInput.java`
- Create: `src/tw/idempiere/invoice/tax/einvoice/EInvoiceDoc.java`
- Test: `test/tw/idempiere/invoice/tax/einvoice/EInvoiceDocTest.java`

**Interfaces:**
- Consumes: `RandomNumberGenerator` (Task 2).
- Produces:
  - `EInvoiceItem(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount)` — immutable line.
  - `EInvoiceInput` — immutable holder of sourced values: `sellerTaxId, sellerName, buyerTaxId (nullable), invoiceNumber, invoiceDate (java.time.LocalDateTime), salesAmount, taxAmount, totalAmount (all BigDecimal), List<EInvoiceItem> items`.
  - `EInvoiceDoc EInvoiceDoc.forB2CTaxable(EInvoiceInput in, String randomNumber)` — applies B2C defaults (`buyerTaxId` blank → `"0000000000"`, `donateMark="0"`, `taxType="1"`, `taxRate=0.05`, `invoiceType="07"`), validates, exposes getters used by the builder.

- [ ] **Step 1: Write the failing test**

```java
package tw.idempiere.invoice.tax.einvoice;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.adempiere.exceptions.AdempiereException;
import org.junit.Test;

public class EInvoiceDocTest {

    private EInvoiceInput input(String buyerTaxId, List<EInvoiceItem> items) {
        return new EInvoiceInput("12345678", "Test Seller Co", buyerTaxId,
                "AA00000001", LocalDateTime.of(2026, 10, 13, 9, 30, 0),
                new BigDecimal("100"), new BigDecimal("5"), new BigDecimal("105"), items);
    }

    private List<EInvoiceItem> oneItem() {
        return Collections.singletonList(
            new EInvoiceItem("Widget", new BigDecimal("1"), new BigDecimal("105"), new BigDecimal("105")));
    }

    @Test
    public void b2cBlankBuyerDefaultsToAllZeros() {
        EInvoiceDoc doc = EInvoiceDoc.forB2CTaxable(input(null, oneItem()), "0007");
        assertEquals("0000000000", doc.getBuyerTaxId());
        assertEquals("0", doc.getDonateMark());
        assertEquals("1", doc.getTaxType());
        assertEquals("0007", doc.getRandomNumber());
        assertEquals("07", doc.getInvoiceType());
    }

    @Test(expected = AdempiereException.class)
    public void rejectsMissingSellerTaxId() {
        EInvoiceInput bad = new EInvoiceInput("", "S", null, "AA00000001",
                LocalDateTime.now(), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, oneItem());
        EInvoiceDoc.forB2CTaxable(bad, "0007");
    }

    @Test(expected = AdempiereException.class)
    public void rejectsNoLines() {
        EInvoiceDoc.forB2CTaxable(input(null, Collections.emptyList()), "0007");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=EInvoiceDocTest`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Write minimal implementation**

`EInvoiceItem.java`:
```java
package tw.idempiere.invoice.tax.einvoice;

import java.math.BigDecimal;

public final class EInvoiceItem {
    public final String description;
    public final BigDecimal quantity, unitPrice, amount;
    public EInvoiceItem(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
        this.description = description; this.quantity = quantity;
        this.unitPrice = unitPrice; this.amount = amount;
    }
}
```

`EInvoiceInput.java`:
```java
package tw.idempiere.invoice.tax.einvoice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class EInvoiceInput {
    public final String sellerTaxId, sellerName, buyerTaxId, invoiceNumber;
    public final LocalDateTime invoiceDate;
    public final BigDecimal salesAmount, taxAmount, totalAmount;
    public final List<EInvoiceItem> items;
    public EInvoiceInput(String sellerTaxId, String sellerName, String buyerTaxId,
                         String invoiceNumber, LocalDateTime invoiceDate,
                         BigDecimal salesAmount, BigDecimal taxAmount, BigDecimal totalAmount,
                         List<EInvoiceItem> items) {
        this.sellerTaxId = sellerTaxId; this.sellerName = sellerName;
        this.buyerTaxId = buyerTaxId; this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate; this.salesAmount = salesAmount;
        this.taxAmount = taxAmount; this.totalAmount = totalAmount; this.items = items;
    }
}
```

`EInvoiceDoc.java`:
```java
package tw.idempiere.invoice.tax.einvoice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.adempiere.exceptions.AdempiereException;

/** Canonical C0401 document — defaults applied + validated. Pure Java. */
public final class EInvoiceDoc {
    private final EInvoiceInput in;
    private final String randomNumber, buyerTaxId;
    private final String donateMark = "0", taxType = "1", invoiceType = "07";
    private final BigDecimal taxRate = new BigDecimal("0.05");

    private EInvoiceDoc(EInvoiceInput in, String randomNumber, String buyerTaxId) {
        this.in = in; this.randomNumber = randomNumber; this.buyerTaxId = buyerTaxId;
    }

    public static EInvoiceDoc forB2CTaxable(EInvoiceInput in, String randomNumber) {
        if (in.sellerTaxId == null || in.sellerTaxId.trim().isEmpty())
            throw new AdempiereException("E-Invoice: seller tax id (賣方統編) is required");
        if (in.items == null || in.items.isEmpty())
            throw new AdempiereException("E-Invoice: at least one line item (品項) is required");
        if (in.invoiceNumber == null || !in.invoiceNumber.matches("[A-Z]{2}\\d{8}"))
            throw new AdempiereException("E-Invoice: invoice number must be AA00000001 format");
        String buyer = (in.buyerTaxId == null || in.buyerTaxId.trim().isEmpty())
                ? "0000000000" : in.buyerTaxId.trim();
        return new EInvoiceDoc(in, randomNumber, buyer);
    }

    public String getSellerTaxId()  { return in.sellerTaxId; }
    public String getSellerName()   { return in.sellerName; }
    public String getBuyerTaxId()   { return buyerTaxId; }
    public String getInvoiceNumber(){ return in.invoiceNumber; }
    public LocalDateTime getInvoiceDate() { return in.invoiceDate; }
    public BigDecimal getSalesAmount()    { return in.salesAmount; }
    public BigDecimal getTaxAmount()      { return in.taxAmount; }
    public BigDecimal getTotalAmount()    { return in.totalAmount; }
    public List<EInvoiceItem> getItems()  { return in.items; }
    public String getRandomNumber() { return randomNumber; }
    public String getDonateMark()   { return donateMark; }
    public String getTaxType()      { return taxType; }
    public String getInvoiceType()  { return invoiceType; }
    public BigDecimal getTaxRate()  { return taxRate; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=EInvoiceDocTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/tw/idempiere/invoice/tax/einvoice/EInvoiceItem.java src/tw/idempiere/invoice/tax/einvoice/EInvoiceInput.java src/tw/idempiere/invoice/tax/einvoice/EInvoiceDoc.java test/tw/idempiere/invoice/tax/einvoice/EInvoiceDocTest.java
git commit -m "feat: add canonical EInvoiceDoc with B2C taxable defaults + validation"
```

---

### Task 4: MigF0401Builder (official-sample-gated) + model extension

**Files:**
- Modify: `src/tw/idempiere/invoice/tax/einvoice/EInvoiceItem.java` (add `taxType`, `sequenceNumber`, nullable `remark`, `relateNumber`)
- Create: `src/tw/idempiere/invoice/tax/einvoice/SellerInfo.java`
- Create: `src/tw/idempiere/invoice/tax/einvoice/BuyerInfo.java`
- Create: `src/tw/idempiere/invoice/tax/einvoice/AmountInfo.java`
- Modify: `src/tw/idempiere/invoice/tax/einvoice/EInvoiceDoc.java` (replace the Task-3 `forB2CTaxable(EInvoiceInput,...)` API with `forB2CPrint(...)` taking the value objects below; keep validation)
- Modify: `test/tw/idempiere/invoice/tax/einvoice/EInvoiceDocTest.java` (adapt to new API: keep "reject blank seller id" and "reject no line items"; drop the obsolete blank-buyer-default test — buyer is now explicit)
- Delete: `src/tw/idempiere/invoice/tax/einvoice/EInvoiceInput.java` (superseded by the value objects; Task 7 sources straight into `forB2CPrint`)
- Create: `src/tw/idempiere/invoice/tax/einvoice/MigF0401Builder.java`
- Create: `test/tw/idempiere/invoice/tax/einvoice/MigF0401BuilderTest.java`

**Authoritative target — the official sample this task MUST reproduce** (`test/resources/mig/F0401-B2C-print-sample.xml`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Invoice xsi:schemaLocation="urn:GEINV:eInvoiceMessage:F0401:4.1 F0401.xsd" xmlns="urn:GEINV:eInvoiceMessage:F0401:4.1" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <Main>
        <InvoiceNumber>KZ10000012</InvoiceNumber>
        <InvoiceDate>20241201</InvoiceDate>
        <InvoiceTime>09:00:00</InvoiceTime>
        <Seller>
            <Identifier>90000011</Identifier>
            <Name>測試公司</Name>
            <Address>台北市中華路1段123號</Address>
            <PersonInCharge>王小明</PersonInCharge>
            <TelephoneNumber>0212345678</TelephoneNumber>
            <FacsimileNumber>0212345678</FacsimileNumber>
            <EmailAddress>test1@test.com.tw</EmailAddress>
            <CustomerNumber>A123456789</CustomerNumber>
            <RoleRemark>defaultUser</RoleRemark>
        </Seller>
        <Buyer>
            <Identifier>0000000000</Identifier>
            <Name>測試買家</Name>
            <Address>台中市中正路888號</Address>
        </Buyer>
        <InvoiceType>07</InvoiceType>
        <DonateMark>0</DonateMark>
        <PrintMark>Y</PrintMark>
        <RandomNumber>1234</RandomNumber>
    </Main>
    <Details>
        <ProductItem>
            <Description>純喫茶紅茶</Description>
            <Quantity>1</Quantity>
            <UnitPrice>20</UnitPrice>
            <TaxType>1</TaxType>
            <Amount>20</Amount>
            <SequenceNumber>001</SequenceNumber>
            <Remark>remark</Remark>
            <RelateNumber>A123456789</RelateNumber>
        </ProductItem>
    </Details>
    <Amount>
        <SalesAmount>20</SalesAmount>
        <FreeTaxSalesAmount>0</FreeTaxSalesAmount>
        <ZeroTaxSalesAmount>0</ZeroTaxSalesAmount>
        <TaxType>1</TaxType>
        <TaxRate>0.05</TaxRate>
        <TaxAmount>0</TaxAmount>
        <TotalAmount>20</TotalAmount>
    </Amount>
</Invoice>
```

**Interfaces (model API this task defines):**
- `EInvoiceItem(String description, BigDecimal quantity, BigDecimal unitPrice, String taxType, BigDecimal amount, String sequenceNumber, String remark, String relateNumber)` — `remark`/`relateNumber` nullable (element omitted when null).
- `SellerInfo(String identifier, String name, String address, String personInCharge, String telephoneNumber, String facsimileNumber, String emailAddress, String customerNumber, String roleRemark)` — everything after `address` nullable (element omitted when null/blank).
- `BuyerInfo(String identifier, String name, String address)` — `name`/`address` nullable.
- `AmountInfo(BigDecimal salesAmount, BigDecimal freeTaxSalesAmount, BigDecimal zeroTaxSalesAmount, String taxType, BigDecimal taxRate, BigDecimal taxAmount, BigDecimal totalAmount)`.
- `EInvoiceDoc.forB2CPrint(String invoiceNumber, LocalDateTime invoiceDateTime, SellerInfo seller, BuyerInfo buyer, String invoiceType, java.util.List<EInvoiceItem> items, AmountInfo amount, String randomNumber)` -> sets `donateMark="0"`, `printMark="Y"`; validates `seller.identifier` non-blank, `items` non-empty, `invoiceNumber` matches `[A-Z]{2}` + 8 digits (throws `org.adempiere.exceptions.AdempiereException`). Exposes getters the builder needs.
- `String MigF0401Builder.build(EInvoiceDoc doc)` -> F0401 XML string. Emits the exact element order shown in the sample; emits a nullable element only when its value is non-null/non-blank. `InvoiceDate` = `yyyyMMdd`, `InvoiceTime` = `HH:mm:ss`, `BigDecimal` via `toPlainString()`. Root attributes in the sample's exact order.

- [ ] **Step 1: Write the failing gate test (reproduce the official sample)**

```java
package tw.idempiere.invoice.tax.einvoice;

import static org.junit.Assert.*;
import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.Test;

public class MigF0401BuilderTest {

    private static String norm(String s) { return s.replaceAll(">\s+<", "><").trim(); }

    private EInvoiceDoc officialPrintSampleDoc() {
        SellerInfo seller = new SellerInfo("90000011", "測試公司", "台北市中華路1段123號",
                "王小明", "0212345678", "0212345678", "test1@test.com.tw", "A123456789", "defaultUser");
        BuyerInfo buyer = new BuyerInfo("0000000000", "測試買家", "台中市中正路888號");
        EInvoiceItem item = new EInvoiceItem("純喫茶紅茶", new BigDecimal("1"), new BigDecimal("20"),
                "1", new BigDecimal("20"), "001", "remark", "A123456789");
        AmountInfo amount = new AmountInfo(new BigDecimal("20"), new BigDecimal("0"), new BigDecimal("0"),
                "1", new BigDecimal("0.05"), new BigDecimal("0"), new BigDecimal("20"));
        return EInvoiceDoc.forB2CPrint("KZ10000012", LocalDateTime.of(2024, 12, 1, 9, 0, 0),
                seller, buyer, "07", Collections.singletonList(item), amount, "1234");
    }

    @Test
    public void reproducesOfficialPrintSample() throws Exception {
        String expected = new String(Files.readAllBytes(
                new File("test/resources/mig/F0401-B2C-print-sample.xml").toPath()), StandardCharsets.UTF_8);
        String actual = MigF0401Builder.build(officialPrintSampleDoc());
        assertEquals(norm(expected), norm(actual));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=MigF0401BuilderTest`
Expected: FAIL — classes/API not defined.

- [ ] **Step 3: Implement value objects, new `EInvoiceDoc.forB2CPrint`, and `MigF0401Builder`**

Value objects = immutable public-final-field holders (same style as existing `EInvoiceItem`). `MigF0401Builder.build` writes the sample's exact structure with an `el(tag,val)` helper and an `elOpt(tag,val)` that returns `""` when `val` is null/blank; XML-escape text. Root line verbatim:
`<Invoice xsi:schemaLocation="urn:GEINV:eInvoiceMessage:F0401:4.1 F0401.xsd" xmlns="urn:GEINV:eInvoiceMessage:F0401:4.1" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">`
Order per sample: Main(InvoiceNumber, InvoiceDate, InvoiceTime, Seller{Identifier,Name,Address,PersonInCharge,TelephoneNumber,FacsimileNumber,EmailAddress,CustomerNumber,RoleRemark}, Buyer{Identifier,Name,Address}, InvoiceType, DonateMark, PrintMark, RandomNumber) -> Details(ProductItem{Description,Quantity,UnitPrice,TaxType,Amount,SequenceNumber,Remark,RelateNumber}) -> Amount(SalesAmount,FreeTaxSalesAmount,ZeroTaxSalesAmount,TaxType,TaxRate,TaxAmount,TotalAmount). Update `EInvoiceDoc` + `EInvoiceDocTest` to the new API; delete `EInvoiceInput.java`.

- [ ] **Step 4: Iterate until the gate test passes**

Run: `mvn test -Dtest=MigF0401BuilderTest,EInvoiceDocTest`
Expected: PASS. If `reproducesOfficialPrintSample` fails, diff the normalized strings and fix element name/order/omission until it matches the official sample. **Do not weaken the test** — fix the builder. Then `mvn test` — full suite green.

- [ ] **Step 5: Commit**

```bash
git add src/tw/idempiere/invoice/tax/einvoice/ test/tw/idempiere/invoice/tax/einvoice/MigF0401BuilderTest.java test/tw/idempiere/invoice/tax/einvoice/EInvoiceDocTest.java
git rm src/tw/idempiere/invoice/tax/einvoice/EInvoiceInput.java
git commit -m "feat: add MigF0401Builder reproducing official MIG 4.1 F0401 B2C sample"
```

### Task 5: TransmissionAdapter + TurnkeyFileAdapter

**Files:**
- Create: `src/tw/idempiere/invoice/tax/einvoice/TransmissionAdapter.java`
- Create: `src/tw/idempiere/invoice/tax/einvoice/TurnkeyFileAdapter.java`
- Test: `test/tw/idempiere/invoice/tax/einvoice/TurnkeyFileAdapterTest.java`

**Interfaces:**
- Produces:
  - `interface TransmissionAdapter { String submit(String invoiceNumber, String xml); }` — returns the written file name; throws on failure.
  - `TurnkeyFileAdapter(String intakeDir)` implements it by writing `<invoiceNumber>.xml` into `intakeDir`.

- [ ] **Step 1: Write the failing test**

```java
package tw.idempiere.invoice.tax.einvoice;

import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import org.adempiere.exceptions.AdempiereException;
import org.junit.Test;

public class TurnkeyFileAdapterTest {

    @Test
    public void writesXmlFileNamedByInvoiceNumber() throws Exception {
        File dir = Files.createTempDirectory("turnkey").toFile();
        TransmissionAdapter a = new TurnkeyFileAdapter(dir.getAbsolutePath());
        String name = a.submit("AA00000001", "<Invoice/>");
        File out = new File(dir, name);
        assertTrue(out.exists());
        assertEquals("AA00000001.xml", name);
        assertEquals("<Invoice/>", new String(Files.readAllBytes(out.toPath()), "UTF-8"));
    }

    @Test(expected = AdempiereException.class)
    public void throwsWhenDirMissing() {
        new TurnkeyFileAdapter("/nonexistent/turnkey/intake").submit("AA00000001", "<Invoice/>");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=TurnkeyFileAdapterTest`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Write minimal implementation**

`TransmissionAdapter.java`:
```java
package tw.idempiere.invoice.tax.einvoice;

/** Hands a MIG XML message off to a transmission backend. */
public interface TransmissionAdapter {
    /** @return the written/submitted file name; throws AdempiereException on failure. */
    String submit(String invoiceNumber, String xml);
}
```

`TurnkeyFileAdapter.java`:
```java
package tw.idempiere.invoice.tax.einvoice;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.adempiere.exceptions.AdempiereException;

/** Drops one C0401 XML file (1 file = 1 invoice) into Turnkey's B2C intake directory. */
public final class TurnkeyFileAdapter implements TransmissionAdapter {

    private final String intakeDir;

    public TurnkeyFileAdapter(String intakeDir) { this.intakeDir = intakeDir; }

    @Override
    public String submit(String invoiceNumber, String xml) {
        File dir = new File(intakeDir);
        if (!dir.isDirectory())
            throw new AdempiereException("Turnkey intake dir not found: " + intakeDir);
        String name = invoiceNumber + ".xml";
        try {
            Files.write(new File(dir, name).toPath(), xml.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AdempiereException("Failed writing C0401 to Turnkey intake: " + e.getMessage(), e);
        }
        return name;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=TurnkeyFileAdapterTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/tw/idempiere/invoice/tax/einvoice/TransmissionAdapter.java src/tw/idempiere/invoice/tax/einvoice/TurnkeyFileAdapter.java test/tw/idempiere/invoice/tax/einvoice/TurnkeyFileAdapterTest.java
git commit -m "feat: add TransmissionAdapter + TurnkeyFileAdapter (file drop)"
```

---

### Task 6: TW_EInvoiceMessage table + model

**Files:**
- Create: `src/tw/idempiere/invoice/tax/model/MEInvoiceMessage.java`
- Modify: `resources/2pack/tw_invoice_system/dict/PackOut.xml` (add `AD_Table`/`AD_Column`/`AD_Window`/`AD_Tab`/`AD_Field`/`AD_Menu` for `TW_EInvoiceMessage`)
- Rename: 2Pack zip `2Pack_1.0.11.zip` → `2Pack_1.0.12.zip`; bump `PackOut.xml Version="1.0.12"`
- Modify: `docs/schema/table-definitions.md` (document the new table)
- Test: `test/tw/idempiere/invoice/tax/model/MEInvoiceMessageTest.java`

**Interfaces:**
- Produces: `MEInvoiceMessage` model with columns per spec §5 and `COLUMNNAME_*` constants + getters/setters following the existing `MInvoicePrefixMap` pattern. Status values: `DRAFT`,`WRITTEN`,`CONFIRMED`,`FAILED`. MessageType: `F0401`.

- [ ] **Step 1: Write the failing test (model constants + accessors)**

```java
package tw.idempiere.invoice.tax.model;

import static org.junit.Assert.*;
import org.junit.Test;

public class MEInvoiceMessageTest {
    @Test public void tableName() {
        assertEquals("TW_EInvoiceMessage", MEInvoiceMessage.Table_Name);
    }
    @Test public void columnConstants() {
        assertEquals("RandomNumber", MEInvoiceMessage.COLUMNNAME_RandomNumber);
        assertEquals("Status", MEInvoiceMessage.COLUMNNAME_Status);
        assertEquals("MessageType", MEInvoiceMessage.COLUMNNAME_MessageType);
        assertEquals("TW_Invoice_Prefix_Map_ID", MEInvoiceMessage.COLUMNNAME_TW_Invoice_Prefix_Map_ID);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=MEInvoiceMessageTest`
Expected: FAIL — class not defined.

- [ ] **Step 3: Write the model (mirror MInvoicePrefixMap exactly)**

```java
package tw.idempiere.invoice.tax.model;

import java.sql.ResultSet;
import java.util.Properties;
import org.adempiere.base.Model;
import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.model.POInfo;

/** TW_EInvoiceMessage — one row per e-invoice transmission attempt. */
@Model(table = MEInvoiceMessage.Table_Name)
public class MEInvoiceMessage extends PO {
    private static final long serialVersionUID = 1L;
    public static final String Table_Name = "TW_EInvoiceMessage";
    public static int Table_ID = 0;

    public static final String COLUMNNAME_TW_EInvoiceMessage_ID = "TW_EInvoiceMessage_ID";
    public static final String COLUMNNAME_TW_EInvoiceMessage_UU = "TW_EInvoiceMessage_UU";
    public static final String COLUMNNAME_TW_Invoice_Prefix_Map_ID = "TW_Invoice_Prefix_Map_ID";
    public static final String COLUMNNAME_MessageType = "MessageType";
    public static final String COLUMNNAME_RandomNumber = "RandomNumber";
    public static final String COLUMNNAME_MigVersion = "MigVersion";
    public static final String COLUMNNAME_Status = "Status";
    public static final String COLUMNNAME_XmlFileName = "XmlFileName";
    public static final String COLUMNNAME_ErrorMessage = "ErrorMessage";
    public static final String COLUMNNAME_GeneratedOn = "GeneratedOn";

    public MEInvoiceMessage(Properties ctx, int id, String trxName) { super(ctx, id, trxName); }
    public MEInvoiceMessage(Properties ctx, ResultSet rs, String trxName) { super(ctx, rs, trxName); }

    @Override protected POInfo initPO(Properties ctx) {
        int tableId = MTable.getTable_ID(Table_Name);
        if (tableId <= 0) return null;
        return POInfo.getPOInfo(ctx, tableId, get_TrxName());
    }
    @Override public int get_AccessLevel() { return 3; }

    public int getTW_Invoice_Prefix_Map_ID() {
        Integer ii = (Integer) get_Value(COLUMNNAME_TW_Invoice_Prefix_Map_ID);
        return ii == null ? 0 : ii;
    }
    public void setTW_Invoice_Prefix_Map_ID(int id) { set_Value(COLUMNNAME_TW_Invoice_Prefix_Map_ID, id); }
    public String getMessageType() { return (String) get_Value(COLUMNNAME_MessageType); }
    public void setMessageType(String v) { set_Value(COLUMNNAME_MessageType, v); }
    public String getRandomNumber() { return (String) get_Value(COLUMNNAME_RandomNumber); }
    public void setRandomNumber(String v) { set_Value(COLUMNNAME_RandomNumber, v); }
    public String getMigVersion() { return (String) get_Value(COLUMNNAME_MigVersion); }
    public void setMigVersion(String v) { set_Value(COLUMNNAME_MigVersion, v); }
    public String getStatus() { return (String) get_Value(COLUMNNAME_Status); }
    public void setStatus(String v) { set_Value(COLUMNNAME_Status, v); }
    public String getXmlFileName() { return (String) get_Value(COLUMNNAME_XmlFileName); }
    public void setXmlFileName(String v) { set_Value(COLUMNNAME_XmlFileName, v); }
    public String getErrorMessage() { return (String) get_Value(COLUMNNAME_ErrorMessage); }
    public void setErrorMessage(String v) { set_Value(COLUMNNAME_ErrorMessage, v); }
}
```

- [ ] **Step 4: Run unit test to verify it passes**

Run: `mvn test -Dtest=MEInvoiceMessageTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Add the table to PackOut.xml (follow existing TW_* blocks)**

Copy the structure of an existing TW_* `AD_Table`+columns block in `PackOut.xml`. Columns: standard set (PK/_UU/audit/AD_Client_ID/AD_Org_ID/IsActive) + `TW_Invoice_Prefix_Map_ID` (TableDir FK, ref 19), `MessageType` (List), `RandomNumber` (String 4), `MigVersion` (String 10), `Status` (List), `XmlFileName` (String 255), `ErrorMessage` (String 2000), `GeneratedOn` (DateTime). New List references `TW E-Invoice Message Type` (value `F0401`) and `TW E-Invoice Status` (`DRAFT`/`WRITTEN`/`CONFIRMED`/`FAILED`) with fresh fixed UUIDs. Every displayed `AD_Field` needs `SeqNoGrid`+`IsDisplayedGrid=Y`; `_UU` `IsUpdateable=Y`. Bump `Version="1.0.12"`.

- [ ] **Step 6: Rebuild the 2Pack zip and verify**

Run:
```bash
cd resources/2pack && rm -f tw_invoice_system/2Pack_*.zip 2>/dev/null; \
zip -r ../../META-INF/2Pack_1.0.12.zip tw_invoice_system && \
unzip -l ../../META-INF/2Pack_1.0.12.zip | grep PackOut.xml
```
Remove the stale `META-INF/2Pack_1.0.11.zip`. Expected: zip lists `tw_invoice_system/dict/PackOut.xml`.

- [ ] **Step 7: Deploy and verify the table is created**

Run `./clean_reinstall.sh` (or `./deploy.sh`), then:
```bash
psql -c "SELECT count(*) FROM pg_tables WHERE schemaname='adempiere' AND tablename='tw_einvoicemessage';"
```
Expected: `1`. Open the new window in the UI, click New — must not throw `IndexOutOfBoundsException` (confirms AD_Field `SeqNoGrid` correctness).

- [ ] **Step 8: Commit**

```bash
git add src/tw/idempiere/invoice/tax/model/MEInvoiceMessage.java test/tw/idempiere/invoice/tax/model/MEInvoiceMessageTest.java resources/2pack/tw_invoice_system/dict/PackOut.xml META-INF/2Pack_1.0.12.zip docs/schema/table-definitions.md
git rm --cached META-INF/2Pack_1.0.11.zip 2>/dev/null; git add -A META-INF/
git commit -m "feat: add TW_EInvoiceMessage table + model (2Pack 1.0.12)"
```

---

### Task 7: GenerateEInvoiceProcess (end-to-end)

> **AMOUNT CORRECTION (authoritative, from MIG 4.1 B2C samples):** do NOT floor-split tax. B2C F0401 puts `SalesAmount = TotalAmount = GrandTotal (含稅)`, `TaxAmount = 0`, each line `Amount = UnitPrice*Qty` (gross), `taxType="1"`. The `splitB2C` helper below is superseded — build `AmountInfo` directly with gross = C_Invoice GrandTotal. (The 401 tax breakdown stays in `TaxCalculationService`, not in the e-invoice message.)

**Files:**
- Create: `src/tw/idempiere/invoice/tax/process/GenerateEInvoiceProcess.java`
- Modify: `src/tw/idempiere/invoice/tax/process/TaiwanProcessFactory.java` (register the new process)
- Modify: `resources/2pack/tw_invoice_system/dict/PackOut.xml` (add `AD_Process` + button/menu to run it on a `TW_Invoice_Prefix_Map` record)
- Test: `test/tw/idempiere/invoice/tax/process/GenerateEInvoiceAmountsTest.java` (pure helper)

**Interfaces:**
- Consumes: `EInvoiceDoc`, `MigF0401Builder`, `TurnkeyFileAdapter`, `RandomNumberGenerator`, `MEInvoiceMessage`, existing `TaxCalculationService`, `MInvoicePrefixMap`.
- Produces: a runnable `SvrProcess` that, given a `TW_Invoice_Prefix_Map_ID`, sources data, builds F0401, drops the file, and writes a `TW_EInvoiceMessage` row.

- [ ] **Step 1: Write a failing unit test for the pure sourcing helper**

```java
package tw.idempiere.invoice.tax.process;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import org.junit.Test;

public class GenerateEInvoiceAmountsTest {
    // 二聯式 B2C: total(gross) known → sales=floor(total/1.05), tax=floor(sales*0.05)
    @Test public void splitsGrossWithFloor() {
        BigDecimal[] r = GenerateEInvoiceProcess.splitB2C(new BigDecimal("105"));
        assertEquals(new BigDecimal("100"), r[0]); // sales
        assertEquals(new BigDecimal("5"),   r[1]); // tax
    }
    @Test public void floorsOddAmount() {
        BigDecimal[] r = GenerateEInvoiceProcess.splitB2C(new BigDecimal("100"));
        assertEquals(new BigDecimal("95"), r[0]); // floor(100/1.05)=95
        assertEquals(new BigDecimal("4"),  r[1]); // floor(95*0.05)=4
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=GenerateEInvoiceAmountsTest`
Expected: FAIL — method not defined.

- [ ] **Step 3: Implement the process + register it**

`GenerateEInvoiceProcess.java`:
```java
package tw.idempiere.invoice.tax.process;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MOrgInfo;
import org.compiere.process.SvrProcess;
import org.compiere.util.Env;
import tw.idempiere.invoice.tax.einvoice.*;
import tw.idempiere.invoice.tax.model.MEInvoiceMessage;
import tw.idempiere.invoice.tax.model.MInvoicePrefixMap;

public class GenerateEInvoiceProcess extends SvrProcess {

    private static final BigDecimal RATE = new BigDecimal("1.05");
    private static final BigDecimal TAX  = new BigDecimal("0.05");

    /** 二聯式: gross → [sales, tax] with FLOOR per Ministry of Finance rule. */
    public static BigDecimal[] splitB2C(BigDecimal gross) {
        BigDecimal sales = gross.divide(RATE, 0, RoundingMode.FLOOR);
        BigDecimal tax = sales.multiply(TAX).setScale(0, RoundingMode.FLOOR);
        return new BigDecimal[]{sales, tax};
    }

    private int p_MapId;

    @Override protected void prepare() {
        p_MapId = getRecord_ID();
    }

    @Override protected String doIt() throws Exception {
        MInvoicePrefixMap map = new MInvoicePrefixMap(getCtx(), p_MapId, get_TrxName());
        if (map.get_ID() <= 0) throw new AdempiereException("Invoice map record not found");

        MInvoice inv = new MInvoice(getCtx(), map.getC_Invoice_ID(), get_TrxName());
        MOrgInfo org = MOrgInfo.get(getCtx(), inv.getAD_Org_ID(), get_TrxName());
        String sellerTaxId = org.getTaxID();

        BigDecimal gross = inv.getGrandTotal();
        BigDecimal[] st = splitB2C(gross);

        List<EInvoiceItem> items = new ArrayList<>();
        for (MInvoiceLine ln : inv.getLines()) {
            items.add(new EInvoiceItem(ln.getName() != null ? ln.getName() : ln.getDescription(),
                    ln.getQtyInvoiced(), ln.getPriceActual(), ln.getLineNetAmt()));
        }

        String random = RandomNumberGenerator.generate();
        EInvoiceInput in = new EInvoiceInput(sellerTaxId, org.getName_Trl(),
                map.getBuyerTaxID(), map.getInvoiceNumber(),
                map.getInvoiceDate().toLocalDateTime(), st[0], st[1], gross, items);
        EInvoiceDoc doc = EInvoiceDoc.forB2CTaxable(in, random);
        String xml = MigF0401Builder.build(doc);

        MEInvoiceMessage msg = new MEInvoiceMessage(getCtx(), 0, get_TrxName());
        msg.setTW_Invoice_Prefix_Map_ID(map.get_ID());
        msg.setMessageType("F0401");
        msg.setRandomNumber(random);
        msg.setMigVersion("4.1");
        msg.set_Value(MEInvoiceMessage.COLUMNNAME_GeneratedOn, new java.sql.Timestamp(System.currentTimeMillis()));

        try {
            String dir = org.compiere.model.MSysConfig.getValue(
                    "TW_TURNKEY_INTAKE_DIR", "/opt/turnkey/B2C", Env.getAD_Client_ID(getCtx()));
            String file = new TurnkeyFileAdapter(dir).submit(doc.getInvoiceNumber(), xml);
            msg.setXmlFileName(file);
            msg.setStatus("WRITTEN");
            msg.saveEx();
            return "F0401 written: " + file;
        } catch (Exception e) {
            msg.setStatus("FAILED");
            msg.setErrorMessage(e.getMessage());
            msg.saveEx();
            throw e;
        }
    }
}
```

Register in `TaiwanProcessFactory.newProcessInstance`:
```java
if (GenerateEInvoiceProcess.class.getName().equals(className))
    return new GenerateEInvoiceProcess();
```

> Note: `org.compiere.model.MInvoice/MInvoiceLine/MOrgInfo/MSysConfig` require `org.compiere.model` in `MANIFEST.MF` `Import-Package` (already present). `org.name_trl`/line accessors are approximate — on first compile, resolve any missing accessor against the real iDempiere 12 API before relying on it.

- [ ] **Step 4: Run unit test to verify it passes**

Run: `mvn test -Dtest=GenerateEInvoiceAmountsTest`
Expected: PASS (2 tests). Then `mvn test` — all tests (89 existing + new) green.

- [ ] **Step 5: Add AD_Process to PackOut.xml + bump 2Pack**

Add an `<AD_Process>` for `GenerateEInvoiceProcess` (Classname = fully-qualified name) and surface it as a toolbar button/menu on the `TW_Invoice_Prefix_Map` window. Bump 2Pack `1.0.12` → `1.0.13`, rename zip, update `Version=`. Follow the existing `GenerateTaxStatementProcess` AD_Process block as the template.

- [ ] **Step 6: Deploy + end-to-end verification**

Run `./clean_reinstall.sh`. In the UI: create an active `TW_InvoicePrefix`, issue a `TW_Invoice_Prefix_Map` row linked to a real `C_Invoice`, run **Generate E-Invoice**. Verify:
```bash
ls -l /opt/turnkey/B2C/   # the <InvoiceNumber>.xml file exists
psql -c "SELECT status, xmlfilename, randomnumber FROM adempiere.tw_einvoicemessage ORDER BY created DESC LIMIT 1;"
xmllint --noout /opt/turnkey/B2C/<InvoiceNumber>.xml && grep -q "eInvoiceMessage:F0401:4.1" /opt/turnkey/B2C/<InvoiceNumber>.xml && echo "LIVE XML OK"
```
Expected: `Status=WRITTEN`, file present, `LIVE XML VALID`.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add GenerateEInvoiceProcess end-to-end F0401 generation (2Pack 1.0.13)"
```

---

## Self-Review notes

- **Spec coverage:** §3 components → Tasks 2–7; §4 data sourcing → Task 7 (`doIt` sources Org/C_Invoice/C_InvoiceLine/map); §5 table → Task 6; §6 first-cut scope → all tasks (carrier/donation/void excluded); §7 error handling → Tasks 3 (validation), 5 (file failure), 7 (FAILED status); §8 testing → Tasks 2–4 pure + Task 4 XSD gate + Task 7 live xmllint; §9 open items → Task 1 (XSD/version), Task 7 Step 3 note (Org tax id / C_Invoice mapping / API accessor check).
- **Known approximations flagged for implementer:** exact F0401 element names/namespace (Task 4 — gated by official-sample reproduction), iDempiere 12 accessor names in Task 7 (`getName_Trl`, line getters — Step 3 note), Turnkey intake subfolder layout (Task 7 via `TW_TURNKEY_INTAKE_DIR` SysConfig). None are silent — each has a verification step.
