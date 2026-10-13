# E-Invoice Transmission (C0401 first cut) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Before writing code**, invoke the Domain Skills declared in this repo's `CLAUDE.md` (`idempiere-mapped-model-factory-service`, `idempiere-annotation-process`) and keep the iDempiere brain rules in mind.

**Goal:** Turn one issued B2C taxable invoice into a valid MIG 4.x `C0401` XML file dropped into Turnkey's intake directory, recorded in a new `TW_EInvoiceMessage` table.

**Architecture:** A new pure-Java `einvoice` sub-package assembles invoice data into a canonical `EInvoiceDoc`, builds C0401 XML (`MigC0401Builder`), and hands it to a pluggable `TransmissionAdapter` (`TurnkeyFileAdapter` = file drop). An iDempiere `SvrProcess` sources the data from existing models and persists a transmission record. Pure pieces are JUnit-tested offline; XML correctness is gated by validation against the official C0401 XSD.

**Tech Stack:** iDempiere 12 OSGi plugin (`tw.idempiere.invoice.tax`), Java, JUnit 4, Maven, 2Pack (PackOut.xml), `javax.xml.validation` + `xmllint` for XSD validation.

## Global Constraints

- Bundle: `tw.idempiere.invoice.tax`; new code under package `tw.idempiere.invoice.tax.einvoice`.
- Monetary math: **`BigDecimal` + `RoundingMode.FLOOR` only** — never `float`/`double`, never `ROUND`. Reuse existing `TaxCalculationService` for amounts.
- Every new iDempiere model class: `@org.adempiere.base.Model(table = ...)`; `initPO()` uses `MTable.getTable_ID(Table_Name)` with `<= 0` guard. Never hardcode `Table_ID`/reference IDs.
- `TaiwanModelFactory` already scans package `tw.idempiere.invoice.tax.model` — new models go there to be auto-discovered.
- Every `SvrProcess` MUST be registered in `TaiwanProcessFactory.newProcessInstance(...)`.
- 2Pack: bump version (`2Pack_1.0.11.zip` → `2Pack_1.0.12.zip` + `PackOut.xml Version=`); keep existing UUIDs; every grid field needs `SeqNoGrid`+`IsDisplayedGrid=Y`; standard AD_Field set per tab; `_UU` `IsUpdateable=Y`.
- **The official MIG 4.x `C0401.xsd` (Task 1) is the authoritative source for element names, namespace, and ordering.** Any XML scaffold in this plan derived from the MIG 3.2 structure must be corrected to pass XSD validation.
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

### Task 4: MigC0401Builder (XSD-gated)

**Files:**
- Create: `src/tw/idempiere/invoice/tax/einvoice/MigC0401Builder.java`
- Test: `test/tw/idempiere/invoice/tax/einvoice/MigC0401BuilderTest.java`

**Interfaces:**
- Consumes: `EInvoiceDoc` (Task 3), the XSD fixture (Task 1).
- Produces: `String MigC0401Builder.build(EInvoiceDoc doc)` → C0401 XML string.

> **Authoritative-source note:** the element tree below follows the MIG 3.2 structure (root `Invoice`; `Main` with `InvoiceNumber`, `InvoiceDate` as `YYYYMMDD`, `InvoiceTime` as `HH:mm:ss`, `Seller/Identifier`+`Name`, `Buyer/Identifier`, `InvoiceType`, `DonateMark`, `RandomNumber`; `Details/ProductItem` with `Description`,`Quantity`,`UnitPrice`,`Amount`,`SequenceNumber`; `Amount` with `SalesAmount`,`TaxType`,`TaxRate`,`TaxAmount`,`TotalAmount`). **Before implementing, open `test/resources/mig/C0401.xsd` (Task 1) and correct tag names, element ORDER, namespace, and any 4.x-added mandatory element.** The XSD-validation test in Step 1 is the gate — make the builder produce XML that validates, not XML that matches this scaffold.

- [ ] **Step 1: Write the failing test (XSD validation is the gate)**

```java
package tw.idempiere.invoice.tax.einvoice;

import static org.junit.Assert.*;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import javax.xml.transform.stream.StreamSource;
import org.junit.Test;

public class MigC0401BuilderTest {

    private EInvoiceDoc sampleDoc() {
        EInvoiceInput in = new EInvoiceInput("12345678", "Test Seller Co", null,
                "AA00000001", LocalDateTime.of(2026, 10, 13, 9, 30, 0),
                new BigDecimal("100"), new BigDecimal("5"), new BigDecimal("105"),
                Collections.singletonList(new EInvoiceItem(
                        "Widget", new BigDecimal("1"), new BigDecimal("105"), new BigDecimal("105"))));
        return EInvoiceDoc.forB2CTaxable(in, "0007");
    }

    @Test
    public void containsKeyFields() {
        String xml = MigC0401Builder.build(sampleDoc());
        assertTrue(xml.contains("<InvoiceNumber>AA00000001</InvoiceNumber>"));
        assertTrue(xml.contains("<InvoiceDate>20261013</InvoiceDate>"));
        assertTrue(xml.contains("<RandomNumber>0007</RandomNumber>"));
        assertTrue(xml.contains("0000000000")); // B2C buyer
    }

    @Test
    public void validatesAgainstOfficialXsd() throws Exception {
        String xml = MigC0401Builder.build(sampleDoc());
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = sf.newSchema(new File("test/resources/mig/C0401.xsd"));
        Validator v = schema.newValidator();
        v.validate(new StreamSource(new StringReader(xml))); // throws if invalid
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=MigC0401BuilderTest`
Expected: FAIL — `MigC0401Builder` not defined.

- [ ] **Step 3: Write the builder (then adjust to pass XSD)**

```java
package tw.idempiere.invoice.tax.einvoice;

import java.time.format.DateTimeFormatter;

/**
 * Builds a MIG C0401 (B2C 開立存證) XML document from an EInvoiceDoc.
 * Element names/namespace/order MUST match test/resources/mig/C0401.xsd (MIG 4.x).
 */
public final class MigC0401Builder {

    // TODO-on-impl: replace with the exact namespace copied from the official sample (Task 1 Step 3).
    private static final String NS = "urn:GEINV:eInvoiceMessage:C0401:4.0";
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm:ss");

    private MigC0401Builder() {}

    public static String build(EInvoiceDoc d) {
        StringBuilder x = new StringBuilder();
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        x.append("<Invoice xmlns=\"").append(NS).append("\">");
        x.append("<Main>");
        x.append(el("InvoiceNumber", d.getInvoiceNumber()));
        x.append(el("InvoiceDate", d.getInvoiceDate().format(D)));
        x.append(el("InvoiceTime", d.getInvoiceDate().format(T)));
        x.append("<Seller>").append(el("Identifier", d.getSellerTaxId()))
         .append(el("Name", d.getSellerName())).append("</Seller>");
        x.append("<Buyer>").append(el("Identifier", d.getBuyerTaxId())).append("</Buyer>");
        x.append(el("InvoiceType", d.getInvoiceType()));
        x.append(el("DonateMark", d.getDonateMark()));
        x.append(el("RandomNumber", d.getRandomNumber()));
        x.append("</Main>");
        x.append("<Details>");
        int seq = 1;
        for (EInvoiceItem it : d.getItems()) {
            x.append("<ProductItem>")
             .append(el("Description", it.description))
             .append(el("Quantity", it.quantity.toPlainString()))
             .append(el("UnitPrice", it.unitPrice.toPlainString()))
             .append(el("Amount", it.amount.toPlainString()))
             .append(el("SequenceNumber", String.valueOf(seq++)))
             .append("</ProductItem>");
        }
        x.append("</Details>");
        x.append("<Amount>")
         .append(el("SalesAmount", d.getSalesAmount().toPlainString()))
         .append(el("TaxType", d.getTaxType()))
         .append(el("TaxRate", d.getTaxRate().toPlainString()))
         .append(el("TaxAmount", d.getTaxAmount().toPlainString()))
         .append(el("TotalAmount", d.getTotalAmount().toPlainString()))
         .append("</Amount>");
        x.append("</Invoice>");
        return x.toString();
    }

    private static String el(String tag, String val) {
        return "<" + tag + ">" + escape(val) + "</" + tag + ">";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
```

- [ ] **Step 4: Iterate until both tests pass**

Run: `mvn test -Dtest=MigC0401BuilderTest`
Expected: PASS (2 tests). If `validatesAgainstOfficialXsd` fails, read the validator error, open `C0401.xsd`, and fix element names/order/namespace until it validates. **Do not weaken the test** — fix the builder.

- [ ] **Step 5: Commit**

```bash
git add src/tw/idempiere/invoice/tax/einvoice/MigC0401Builder.java test/tw/idempiere/invoice/tax/einvoice/MigC0401BuilderTest.java
git commit -m "feat: add MigC0401Builder gated by official C0401 XSD validation"
```

---

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
- Produces: `MEInvoiceMessage` model with columns per spec §5 and `COLUMNNAME_*` constants + getters/setters following the existing `MInvoicePrefixMap` pattern. Status values: `DRAFT`,`WRITTEN`,`CONFIRMED`,`FAILED`. MessageType: `C0401`.

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

Copy the structure of an existing TW_* `AD_Table`+columns block in `PackOut.xml`. Columns: standard set (PK/_UU/audit/AD_Client_ID/AD_Org_ID/IsActive) + `TW_Invoice_Prefix_Map_ID` (TableDir FK, ref 19), `MessageType` (List), `RandomNumber` (String 4), `MigVersion` (String 10), `Status` (List), `XmlFileName` (String 255), `ErrorMessage` (String 2000), `GeneratedOn` (DateTime). New List references `TW E-Invoice Message Type` (value `C0401`) and `TW E-Invoice Status` (`DRAFT`/`WRITTEN`/`CONFIRMED`/`FAILED`) with fresh fixed UUIDs. Every displayed `AD_Field` needs `SeqNoGrid`+`IsDisplayedGrid=Y`; `_UU` `IsUpdateable=Y`. Bump `Version="1.0.12"`.

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

**Files:**
- Create: `src/tw/idempiere/invoice/tax/process/GenerateEInvoiceProcess.java`
- Modify: `src/tw/idempiere/invoice/tax/process/TaiwanProcessFactory.java` (register the new process)
- Modify: `resources/2pack/tw_invoice_system/dict/PackOut.xml` (add `AD_Process` + button/menu to run it on a `TW_Invoice_Prefix_Map` record)
- Test: `test/tw/idempiere/invoice/tax/process/GenerateEInvoiceAmountsTest.java` (pure helper)

**Interfaces:**
- Consumes: `EInvoiceDoc`, `MigC0401Builder`, `TurnkeyFileAdapter`, `RandomNumberGenerator`, `MEInvoiceMessage`, existing `TaxCalculationService`, `MInvoicePrefixMap`.
- Produces: a runnable `SvrProcess` that, given a `TW_Invoice_Prefix_Map_ID`, sources data, builds C0401, drops the file, and writes a `TW_EInvoiceMessage` row.

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
        String xml = MigC0401Builder.build(doc);

        MEInvoiceMessage msg = new MEInvoiceMessage(getCtx(), 0, get_TrxName());
        msg.setTW_Invoice_Prefix_Map_ID(map.get_ID());
        msg.setMessageType("C0401");
        msg.setRandomNumber(random);
        msg.setMigVersion("4.0");
        msg.set_Value(MEInvoiceMessage.COLUMNNAME_GeneratedOn, new java.sql.Timestamp(System.currentTimeMillis()));

        try {
            String dir = org.compiere.model.MSysConfig.getValue(
                    "TW_TURNKEY_INTAKE_DIR", "/opt/turnkey/B2C", Env.getAD_Client_ID(getCtx()));
            String file = new TurnkeyFileAdapter(dir).submit(doc.getInvoiceNumber(), xml);
            msg.setXmlFileName(file);
            msg.setStatus("WRITTEN");
            msg.saveEx();
            return "C0401 written: " + file;
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
xmllint --noout --schema test/resources/mig/C0401.xsd /opt/turnkey/B2C/<InvoiceNumber>.xml && echo "LIVE XML VALID"
```
Expected: `Status=WRITTEN`, file present, `LIVE XML VALID`.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add GenerateEInvoiceProcess end-to-end C0401 generation (2Pack 1.0.13)"
```

---

## Self-Review notes

- **Spec coverage:** §3 components → Tasks 2–7; §4 data sourcing → Task 7 (`doIt` sources Org/C_Invoice/C_InvoiceLine/map); §5 table → Task 6; §6 first-cut scope → all tasks (carrier/donation/void excluded); §7 error handling → Tasks 3 (validation), 5 (file failure), 7 (FAILED status); §8 testing → Tasks 2–4 pure + Task 4 XSD gate + Task 7 live xmllint; §9 open items → Task 1 (XSD/version), Task 7 Step 3 note (Org tax id / C_Invoice mapping / API accessor check).
- **Known approximations flagged for implementer:** exact C0401 element names/namespace (Task 4 note — XSD-gated), iDempiere 12 accessor names in Task 7 (`getName_Trl`, line getters — Step 3 note), Turnkey intake subfolder layout (Task 7 via `TW_TURNKEY_INTAKE_DIR` SysConfig). None are silent — each has a verification step.
