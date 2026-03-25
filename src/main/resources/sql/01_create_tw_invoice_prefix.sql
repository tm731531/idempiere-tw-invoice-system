-- ===========================================================================
-- Taiwan Invoice Tax System - TW_InvoicePrefix Table Definition
-- ===========================================================================
-- Table: TW_InvoicePrefix
-- Purpose: Manages Taiwan invoice prefix codes (字軌) such as AA, AB, AC, etc.
--          Each prefix represents a block of invoice numbers assigned by the
--          Ministry of Finance (財政部).
--
-- Author: Taiwan iDempiere Community
-- Version: 1.0.0
-- ===========================================================================

CREATE TABLE IF NOT EXISTS TW_InvoicePrefix (
    -- Primary Key
    TW_InvoicePrefix_ID     NUMERIC(10,0)   NOT NULL,

    -- Standard iDempiere Columns
    AD_Client_ID            NUMERIC(10,0)   NOT NULL DEFAULT 0,
    AD_Org_ID               NUMERIC(10,0)   NOT NULL DEFAULT 0,
    IsActive                CHAR(1)         NOT NULL DEFAULT 'Y' CHECK (IsActive IN ('Y','N')),
    Created                 TIMESTAMP       NOT NULL DEFAULT NOW(),
    CreatedBy               NUMERIC(10,0)   NOT NULL DEFAULT 0,
    Updated                 TIMESTAMP       NOT NULL DEFAULT NOW(),
    UpdatedBy               NUMERIC(10,0)   NOT NULL DEFAULT 0,

    -- UUID for replication support
    TW_InvoicePrefix_UU     CHAR(36),

    -- Business Columns
    -- PrefixCode: 2-character uppercase invoice prefix (e.g., AA, AB, ZZ)
    PrefixCode              CHAR(2)         NOT NULL,

    -- InvoiceType: Type of invoice this prefix is assigned to
    --   SALES_TRIPART = Three-part (三聯式) invoice
    --   SALES_BIPART  = Two-part (二聯式) invoice
    InvoiceType             VARCHAR(20)     NOT NULL CHECK (InvoiceType IN ('SALES_TRIPART','SALES_BIPART')),

    -- Invoice number range for this prefix block
    StartNumber             NUMERIC(8,0)    NOT NULL DEFAULT 1,
    EndNumber               NUMERIC(8,0)    NOT NULL,

    -- Current state tracking
    CurrentNumber           NUMERIC(8,0)    NOT NULL DEFAULT 0,
    LastInvoiceNumber       VARCHAR(10),
    LastIssuedInvoiceDate   TIMESTAMP,

    -- Status of this prefix block
    --   A = Active   (currently in use)
    --   I = Inactive (not yet active or suspended)
    --   C = Complete (all numbers in range have been used)
    Status                  CHAR(1)         NOT NULL DEFAULT 'A' CHECK (Status IN ('A','I','C')),

    -- Invoice prefix validity period (財政部核配字軌有效期間，每2個月一期)
    PrefixStartDate         DATE            NOT NULL,
    PrefixEndDate           DATE            NOT NULL,

    -- Constraints
    CONSTRAINT TW_InvoicePrefix_Key     PRIMARY KEY (TW_InvoicePrefix_ID),
    CONSTRAINT TW_InvoicePrefix_UU_Key  UNIQUE (TW_InvoicePrefix_UU),
    CONSTRAINT TW_InvoicePrefix_Range   CHECK (EndNumber >= StartNumber),
    CONSTRAINT TW_InvoicePrefix_PrefixFmt CHECK (PrefixCode ~ '^[A-Z]{2}$'),
    CONSTRAINT TW_InvoicePrefix_PeriodCheck CHECK (PrefixEndDate >= PrefixStartDate),

    -- Foreign Keys to iDempiere standard tables
    CONSTRAINT TW_InvoicePrefix_Client  FOREIGN KEY (AD_Client_ID)
        REFERENCES AD_Client(AD_Client_ID),
    CONSTRAINT TW_InvoicePrefix_Org     FOREIGN KEY (AD_Org_ID)
        REFERENCES AD_Org(AD_Org_ID)
);

COMMENT ON TABLE TW_InvoicePrefix IS 'Taiwan Invoice Prefix (字軌) management table';
COMMENT ON COLUMN TW_InvoicePrefix.PrefixCode IS '2-character uppercase invoice prefix code (e.g., AA, AB, ZZ)';
COMMENT ON COLUMN TW_InvoicePrefix.InvoiceType IS 'Invoice type: SALES_TRIPART (三聯式) or SALES_BIPART (二聯式)';
COMMENT ON COLUMN TW_InvoicePrefix.StartNumber IS 'First invoice number in this prefix block (>= 1)';
COMMENT ON COLUMN TW_InvoicePrefix.EndNumber IS 'Last invoice number in this prefix block (>= StartNumber)';
COMMENT ON COLUMN TW_InvoicePrefix.CurrentNumber IS 'Current invoice number to be issued next (0 = not yet initialized)';
COMMENT ON COLUMN TW_InvoicePrefix.LastInvoiceNumber IS 'The last invoice number that was successfully issued';
COMMENT ON COLUMN TW_InvoicePrefix.LastIssuedInvoiceDate IS 'Date and time of the last invoice issued with this prefix';
COMMENT ON COLUMN TW_InvoicePrefix.Status IS 'A=Active, I=Inactive, C=Complete (all numbers used)';
COMMENT ON COLUMN TW_InvoicePrefix.PrefixStartDate IS 'Start date of this prefix validity period (assigned by Ministry of Finance)';
COMMENT ON COLUMN TW_InvoicePrefix.PrefixEndDate IS 'End date of this prefix validity period (typically 2 months after start)';

-- ===========================================================================
-- Indexes
-- ===========================================================================

-- Index for querying prefixes by organization (most common lookup pattern)
CREATE INDEX IF NOT EXISTS idx_tw_invoiceprefix_org
    ON TW_InvoicePrefix (AD_Org_ID, IsActive, Status);

-- Index for querying prefixes by invoice type
CREATE INDEX IF NOT EXISTS idx_tw_invoiceprefix_type
    ON TW_InvoicePrefix (AD_Client_ID, InvoiceType, Status);

-- Index on PrefixCode for direct lookups
CREATE INDEX IF NOT EXISTS idx_tw_invoiceprefix_code
    ON TW_InvoicePrefix (AD_Client_ID, AD_Org_ID, PrefixCode);
