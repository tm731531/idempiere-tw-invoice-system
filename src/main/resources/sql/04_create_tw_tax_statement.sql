-- ===========================================================================
-- Taiwan Invoice Tax System - TW_TaxStatement Table Definition
-- ===========================================================================
-- Table: TW_TaxStatement
-- Purpose: Stores the compiled VAT return (401 form) for each bimonthly
--          reporting period.  Supports both ordinary businesses and mixed-
--          business operators (兼營營業人) that must apportion input tax
--          between taxable and exempt activities.
--
-- Author: Taiwan iDempiere Community
-- Version: 1.0.0
-- ===========================================================================

CREATE TABLE IF NOT EXISTS TW_TaxStatement (
    -- Primary Key
    TW_TaxStatement_ID          NUMERIC(10,0)   NOT NULL,

    -- Standard iDempiere Columns
    AD_Client_ID                NUMERIC(10,0)   NOT NULL DEFAULT 0,
    AD_Org_ID                   NUMERIC(10,0)   NOT NULL DEFAULT 0,
    IsActive                    CHAR(1)         NOT NULL DEFAULT 'Y' CHECK (IsActive IN ('Y','N')),
    Created                     TIMESTAMP       NOT NULL DEFAULT NOW(),
    CreatedBy                   NUMERIC(10,0)   NOT NULL DEFAULT 0,
    Updated                     TIMESTAMP       NOT NULL DEFAULT NOW(),
    UpdatedBy                   NUMERIC(10,0)   NOT NULL DEFAULT 0,

    -- UUID for replication support
    TW_TaxStatement_UU          CHAR(36),

    -- Business Columns
    -- StatementPeriod: Bimonthly period identifier (1–6 per calendar year).
    --   1 = Jan-Feb, 2 = Mar-Apr, 3 = May-Jun,
    --   4 = Jul-Aug, 5 = Sep-Oct, 6 = Nov-Dec
    StatementPeriod             NUMERIC(1,0)    NOT NULL
                                    CHECK (StatementPeriod BETWEEN 1 AND 6),

    -- StatementYear: Calendar year this statement belongs to (e.g. 2026)
    StatementYear               NUMERIC(4,0)    NOT NULL CHECK (StatementYear >= 2000),

    -- TaxableRevenue: Sales subject to 5% VAT (應稅銷售額), in TWD
    TaxableRevenue              NUMERIC(18,2)   NOT NULL DEFAULT 0
                                    CHECK (TaxableRevenue >= 0),

    -- ExemptRevenue: Sales exempt from VAT (免稅銷售額), in TWD
    ExemptRevenue               NUMERIC(18,2)   NOT NULL DEFAULT 0
                                    CHECK (ExemptRevenue >= 0),

    -- OutputTaxAmount: VAT collected on sales (銷項稅額), in TWD
    OutputTaxAmount             NUMERIC(18,2)   NOT NULL DEFAULT 0
                                    CHECK (OutputTaxAmount >= 0),

    -- InputTaxAmount: VAT paid on purchases before apportionment (進項稅額), in TWD
    InputTaxAmount              NUMERIC(18,2)   NOT NULL DEFAULT 0
                                    CHECK (InputTaxAmount >= 0),

    -- TaxPayable: Net VAT due = OutputTaxAmount - (apportioned) InputTaxAmount.
    --   A negative value represents a refundable credit.
    TaxPayable                  NUMERIC(18,2)   NOT NULL DEFAULT 0,

    -- Zero-rate sales (零稅率銷售額) - export sales, eligible for tax refund (not same as exempt)
    ZeroRateSalesAmount         NUMERIC(15,2)   NOT NULL DEFAULT 0,

    -- Non-deductible input tax (不可扣抵進項稅額) - e.g., entertainment, luxury goods
    NonDeductibleInputTax       NUMERIC(15,2)   NOT NULL DEFAULT 0,

    -- Carry-over tax credit from previous period (前期累積留抵稅額)
    CarryOverTaxCredit          NUMERIC(15,2)   NOT NULL DEFAULT 0,

    -- Filing due date (申報截止日 = 次月15日，遇假日順延)
    FilingDueDate               DATE,

    -- IsMixedBusiness: 'Y' for 兼營營業人 (mixed taxable/exempt operator)
    IsMixedBusiness             CHAR(1)         NOT NULL DEFAULT 'N'
                                    CHECK (IsMixedBusiness IN ('Y','N')),

    -- MixedBusinessRatio: Taxable-revenue proportion used to apportion input
    --   tax for mixed-business operators (0.0000–1.0000).
    --   NULL when IsMixedBusiness='N'.
    MixedBusinessRatio          NUMERIC(7,4)
                                    CHECK (MixedBusinessRatio IS NULL
                                           OR (MixedBusinessRatio >= 0
                                               AND MixedBusinessRatio <= 1)),

    -- Constraints
    CONSTRAINT TW_TaxStmt_Key       PRIMARY KEY (TW_TaxStatement_ID),
    CONSTRAINT TW_TaxStmt_UU_Key    UNIQUE (TW_TaxStatement_UU),

    -- One statement per organisation per period per year
    CONSTRAINT TW_TaxStmt_Period    UNIQUE (AD_Org_ID, StatementYear, StatementPeriod),

    -- Foreign Keys
    CONSTRAINT TW_TaxStmt_Client    FOREIGN KEY (AD_Client_ID)
        REFERENCES AD_Client(AD_Client_ID),
    CONSTRAINT TW_TaxStmt_Org       FOREIGN KEY (AD_Org_ID)
        REFERENCES AD_Org(AD_Org_ID)
);

COMMENT ON TABLE TW_TaxStatement IS 'Bimonthly VAT return (401 form) aggregated per organisation and reporting period';
COMMENT ON COLUMN TW_TaxStatement.StatementPeriod IS '1–6: bimonthly period within the calendar year (1=Jan-Feb … 6=Nov-Dec)';
COMMENT ON COLUMN TW_TaxStatement.StatementYear IS 'Calendar year (e.g. 2026)';
COMMENT ON COLUMN TW_TaxStatement.TaxableRevenue IS 'Sales subject to 5% VAT (應稅銷售額) in TWD';
COMMENT ON COLUMN TW_TaxStatement.ExemptRevenue IS 'VAT-exempt sales (免稅銷售額) in TWD';
COMMENT ON COLUMN TW_TaxStatement.OutputTaxAmount IS 'VAT collected on sales (銷項稅額) in TWD';
COMMENT ON COLUMN TW_TaxStatement.InputTaxAmount IS 'VAT paid on purchases before mixed-business apportionment (進項稅額) in TWD';
COMMENT ON COLUMN TW_TaxStatement.TaxPayable IS 'Net VAT payable = OutputTax - apportioned InputTax; negative = refund due';
COMMENT ON COLUMN TW_TaxStatement.IsMixedBusiness IS 'Y=兼營營業人 (mixed taxable/exempt operator), N=ordinary taxable business';
COMMENT ON COLUMN TW_TaxStatement.MixedBusinessRatio IS 'Taxable-revenue ratio for input-tax apportionment (0.0000–1.0000); NULL for ordinary businesses';
COMMENT ON COLUMN TW_TaxStatement.ZeroRateSalesAmount IS 'Zero-rate sales amount (零稅率，主要為出口), reportable separately, eligible for refund';
COMMENT ON COLUMN TW_TaxStatement.NonDeductibleInputTax IS 'Input tax that cannot be deducted (不可扣抵進項稅額)';
COMMENT ON COLUMN TW_TaxStatement.CarryOverTaxCredit IS 'Accumulated carry-over credit from previous period (前期留抵稅額)';
COMMENT ON COLUMN TW_TaxStatement.FilingDueDate IS 'Filing deadline: 15th of month following period end (next working day if holiday)';

-- ===========================================================================
-- Indexes
-- ===========================================================================

-- Primary reporting lookup: statements by year and period
CREATE INDEX IF NOT EXISTS idx_tw_taxstmt_period
    ON TW_TaxStatement (AD_Client_ID, StatementYear, StatementPeriod);

-- Organisation-specific reporting
CREATE INDEX IF NOT EXISTS idx_tw_taxstmt_org
    ON TW_TaxStatement (AD_Org_ID, StatementYear, StatementPeriod);

-- Mixed-business filter (for apportionment recalculation runs)
CREATE INDEX IF NOT EXISTS idx_tw_taxstmt_mixed
    ON TW_TaxStatement (AD_Client_ID, IsMixedBusiness, StatementYear)
    WHERE IsMixedBusiness = 'Y';
