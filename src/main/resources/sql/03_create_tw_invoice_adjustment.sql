-- ===========================================================================
-- Taiwan Invoice Tax System - TW_InvoiceAdjustment Table Definition
-- ===========================================================================
-- Table: TW_InvoiceAdjustment
-- Purpose: Records input-tax deduction adjustments (折讓/退回) linked to a
--          previously issued invoice.  Tracks whether the adjustment was
--          reported within the required VAT filing period and raises an
--          overdue warning when the deadline is missed.
--
-- Author: Taiwan iDempiere Community
-- Version: 1.0.0
-- ===========================================================================

CREATE TABLE IF NOT EXISTS TW_InvoiceAdjustment (
    -- Primary Key
    TW_InvoiceAdjustment_ID     NUMERIC(10,0)   NOT NULL,

    -- Standard iDempiere Columns
    AD_Client_ID                NUMERIC(10,0)   NOT NULL DEFAULT 0,
    AD_Org_ID                   NUMERIC(10,0)   NOT NULL DEFAULT 0,
    IsActive                    CHAR(1)         NOT NULL DEFAULT 'Y' CHECK (IsActive IN ('Y','N')),
    Created                     TIMESTAMP       NOT NULL DEFAULT NOW(),
    CreatedBy                   NUMERIC(10,0)   NOT NULL DEFAULT 0,
    Updated                     TIMESTAMP       NOT NULL DEFAULT NOW(),
    UpdatedBy                   NUMERIC(10,0)   NOT NULL DEFAULT 0,

    -- UUID for replication support
    TW_InvoiceAdjustment_UU     CHAR(36),

    -- Foreign Key to the original invoice map record
    TW_InvoicePrefixMap_ID      NUMERIC(10,0)   NOT NULL,

    -- Business Columns
    -- AdjustmentType: nature of the adjustment
    --   RETURN    = Goods return (退回)
    --   ALLOWANCE = Price allowance / debit note (折讓)
    AdjustmentType              VARCHAR(20)     NOT NULL
                                    CHECK (AdjustmentType IN ('RETURN','ALLOWANCE')),

    -- AdjustmentAmount: Total adjustment amount (positive value, in TWD)
    AdjustmentAmount            NUMERIC(18,2)   NOT NULL CHECK (AdjustmentAmount > 0),

    -- InputTaxAmount: Input tax portion of the adjustment (positive value)
    InputTaxAmount              NUMERIC(18,2)   NOT NULL DEFAULT 0
                                    CHECK (InputTaxAmount >= 0),

    -- RequiredReportingPeriod: The bimonthly period (YYYYMM, first month of
    --   the two-month window) by which this adjustment must be reported.
    RequiredReportingPeriod     CHAR(6)         NOT NULL,

    -- IsReportedOnTime: 'Y' when the adjustment was filed within the required
    --                   reporting period; 'N' otherwise.
    IsReportedOnTime            CHAR(1)         NOT NULL DEFAULT 'Y'
                                    CHECK (IsReportedOnTime IN ('Y','N')),

    -- OverdueWarning: Human-readable warning message when IsReportedOnTime='N'.
    OverdueWarning              VARCHAR(500),

    -- Constraints
    CONSTRAINT TW_InvoiceAdj_Key        PRIMARY KEY (TW_InvoiceAdjustment_ID),
    CONSTRAINT TW_InvoiceAdj_UU_Key     UNIQUE (TW_InvoiceAdjustment_UU),

    -- Foreign Keys
    CONSTRAINT TW_InvAdj_Client         FOREIGN KEY (AD_Client_ID)
        REFERENCES AD_Client(AD_Client_ID),
    CONSTRAINT TW_InvAdj_Org            FOREIGN KEY (AD_Org_ID)
        REFERENCES AD_Org(AD_Org_ID),
    CONSTRAINT TW_InvAdj_PrefixMap      FOREIGN KEY (TW_InvoicePrefixMap_ID)
        REFERENCES TW_Invoice_Prefix_Map(TW_InvoicePrefixMap_ID)
);

COMMENT ON TABLE TW_InvoiceAdjustment IS 'Input-tax deduction adjustments (退回/折讓) for Taiwan VAT filing';
COMMENT ON COLUMN TW_InvoiceAdjustment.AdjustmentType IS 'RETURN=退回 (goods return), ALLOWANCE=折讓 (price allowance)';
COMMENT ON COLUMN TW_InvoiceAdjustment.AdjustmentAmount IS 'Total adjustment amount in TWD (must be positive)';
COMMENT ON COLUMN TW_InvoiceAdjustment.InputTaxAmount IS 'Input tax component of the adjustment (must be >= 0)';
COMMENT ON COLUMN TW_InvoiceAdjustment.RequiredReportingPeriod IS 'Bimonthly period (YYYYMM, first month) by which the adjustment must be filed';
COMMENT ON COLUMN TW_InvoiceAdjustment.IsReportedOnTime IS 'Y=filed within required period, N=overdue';
COMMENT ON COLUMN TW_InvoiceAdjustment.OverdueWarning IS 'Warning message when IsReportedOnTime=N';

-- ===========================================================================
-- Indexes
-- ===========================================================================

-- Lookup adjustments for a specific invoice map
CREATE INDEX IF NOT EXISTS idx_tw_invadj_map
    ON TW_InvoiceAdjustment (TW_InvoicePrefixMap_ID);

-- Period-based reporting sweep
CREATE INDEX IF NOT EXISTS idx_tw_invadj_period
    ON TW_InvoiceAdjustment (AD_Client_ID, RequiredReportingPeriod, IsReportedOnTime);

-- Overdue warning sweep
CREATE INDEX IF NOT EXISTS idx_tw_invadj_overdue
    ON TW_InvoiceAdjustment (AD_Client_ID, IsReportedOnTime)
    WHERE IsReportedOnTime = 'N';

-- Organisation filter
CREATE INDEX IF NOT EXISTS idx_tw_invadj_org
    ON TW_InvoiceAdjustment (AD_Org_ID, AdjustmentType);
