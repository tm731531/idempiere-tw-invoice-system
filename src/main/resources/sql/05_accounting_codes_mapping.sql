-- ===========================================================================
-- Taiwan Invoice Tax System - Accounting Code Mapping Reference Queries
-- ===========================================================================
-- Purpose: Reference SQL for querying accounting codes from the iDempiere
--          C_ElementValue table.  These queries are used by AccountingCodeMapper
--          at runtime to resolve Taiwan chart-of-accounts codes dynamically.
--
-- NOTE: This file is for REFERENCE ONLY.  The Java service AccountingCodeMapper
--       executes equivalent queries through iDempiere's DB utility class.
--       Do NOT execute this file directly in production.
--
-- Author: Taiwan iDempiere Community
-- Version: 1.0.0
-- ===========================================================================


-- ---------------------------------------------------------------------------
-- Query 1: Fetch all active leaf accounts for a specific tenant
-- ---------------------------------------------------------------------------
-- Used by AccountingCodeMapper.loadAccountsFromDB() to populate the cache.
-- Replace :ad_client_id with the actual AD_Client_ID value.
--
-- Key columns:
--   ev.Value        – The account code string (e.g. '1112', '2121')
--   ev.Name         – The Chinese description
--   ev.AccountType  – A=Asset, L=Liability, R=Revenue, E=Expense
--   ev.IsSummary    – 'N' = leaf account (postable), 'Y' = header/summary
-- ---------------------------------------------------------------------------

SELECT
    ev.Value,
    ev.Name,
    ev.AccountType,
    ev.C_ElementValue_ID,
    ev.IsSummary,
    e.Name          AS ElementName,
    e.ElementType
FROM
    C_ElementValue ev
    JOIN C_Element e ON (e.C_Element_ID = ev.C_Element_ID)
WHERE
    ev.AD_Client_ID = :ad_client_id
    AND ev.IsActive = 'Y'
    AND ev.IsSummary = 'N'
    AND e.ElementType = 'A'           -- 'A' = Account element (chart of accounts)
ORDER BY
    ev.Value;


-- ---------------------------------------------------------------------------
-- Query 2: Look up a single account by Value for a specific tenant
-- ---------------------------------------------------------------------------
-- Used when AccountingCodeMapper.lookupAccount() needs to verify one code.
-- Replace :ad_client_id and :account_value with actual values.
-- ---------------------------------------------------------------------------

SELECT
    ev.Value,
    ev.Name,
    ev.AccountType,
    ev.C_ElementValue_ID,
    ev.Description,
    ev.IsSummary
FROM
    C_ElementValue ev
    JOIN C_Element e ON (e.C_Element_ID = ev.C_Element_ID)
WHERE
    ev.AD_Client_ID = :ad_client_id
    AND ev.Value    = :account_value
    AND ev.IsActive = 'Y'
    AND e.ElementType = 'A';


-- ---------------------------------------------------------------------------
-- Query 3: Validate all five required Taiwan Invoice Tax accounts
-- ---------------------------------------------------------------------------
-- Run this diagnostic query to confirm all mandatory accounts are present
-- before deploying the Taiwan Invoice Tax plugin.
-- Replace :ad_client_id with the actual AD_Client_ID value.
-- ---------------------------------------------------------------------------

SELECT
    ev.Value,
    ev.Name,
    ev.AccountType,
    ev.IsActive,
    ev.IsSummary,
    CASE ev.Value
        WHEN '1112' THEN '應收進項稅額 (Input Tax Receivable)'
        WHEN '2121' THEN '應付營業稅 (Output Tax Payable)'
        WHEN '4100' THEN '銷售收入-應稅 (Taxable Sales Revenue)'
        WHEN '4200' THEN '銷售收入-免稅 (Exempt Sales Revenue)'
        WHEN '5100' THEN '銷貨成本 (Cost of Goods Sold)'
        ELSE 'Unknown'
    END                             AS TW_AccountDescription,
    CASE
        WHEN ev.IsActive  = 'Y'
         AND ev.IsSummary = 'N'
        THEN 'OK'
        ELSE 'PROBLEM'
    END                             AS Status
FROM
    C_ElementValue ev
    JOIN C_Element e ON (e.C_Element_ID = ev.C_Element_ID)
WHERE
    ev.AD_Client_ID = :ad_client_id
    AND ev.Value IN ('1112', '2121', '4100', '4200', '5100')
    AND e.ElementType = 'A'
ORDER BY
    ev.Value;


-- ---------------------------------------------------------------------------
-- Query 4: Find the Account chart-of-accounts element for a tenant
-- ---------------------------------------------------------------------------
-- Use this query to identify which C_Element is used for the Account
-- chart of accounts in a given tenant.
-- ---------------------------------------------------------------------------

SELECT
    e.C_Element_ID,
    e.Name,
    e.ElementType,
    e.AD_Client_ID
FROM
    C_Element e
WHERE
    e.AD_Client_ID = :ad_client_id
    AND e.ElementType = 'A'
    AND e.IsActive = 'Y'
ORDER BY
    e.Name;


-- ---------------------------------------------------------------------------
-- Query 5: Count accounts by type for a tenant (diagnostic)
-- ---------------------------------------------------------------------------
-- Provides a summary of how many accounts of each type are loaded.
-- Useful for verifying that the chart of accounts was imported correctly.
-- ---------------------------------------------------------------------------

SELECT
    ev.AccountType,
    CASE ev.AccountType
        WHEN 'A' THEN '資產 (Asset)'
        WHEN 'L' THEN '負債 (Liability)'
        WHEN 'R' THEN '收入 (Revenue)'
        WHEN 'E' THEN '支出 (Expense)'
        ELSE 'Other'
    END                             AS TypeDescription,
    COUNT(*)                        AS TotalAccounts,
    SUM(CASE WHEN ev.IsSummary = 'N' THEN 1 ELSE 0 END) AS LeafAccounts,
    SUM(CASE WHEN ev.IsSummary = 'Y' THEN 1 ELSE 0 END) AS SummaryAccounts
FROM
    C_ElementValue ev
    JOIN C_Element e ON (e.C_Element_ID = ev.C_Element_ID)
WHERE
    ev.AD_Client_ID = :ad_client_id
    AND ev.IsActive = 'Y'
    AND e.ElementType = 'A'
GROUP BY
    ev.AccountType
ORDER BY
    ev.AccountType;
