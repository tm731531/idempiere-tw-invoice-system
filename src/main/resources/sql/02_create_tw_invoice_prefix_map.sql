-- ===========================================================================
-- Taiwan Invoice Tax System - TW_Invoice_Prefix_Map Table Definition
-- ===========================================================================
-- Table: TW_Invoice_Prefix_Map
-- Purpose: Maps each C_Invoice record to its Taiwan invoice prefix (字軌) and
--          tracks per-invoice validation flags such as date-sequence integrity,
--          month consistency, and 10-year input-tax expiry warnings.
--
-- Author: Taiwan iDempiere Community
-- Version: 1.0.0
-- ===========================================================================

CREATE TABLE IF NOT EXISTS TW_Invoice_Prefix_Map (
    -- Primary Key
    TW_InvoicePrefixMap_ID      NUMERIC(10,0)   NOT NULL,

    -- Standard iDempiere Columns
    AD_Client_ID                NUMERIC(10,0)   NOT NULL DEFAULT 0,
    AD_Org_ID                   NUMERIC(10,0)   NOT NULL DEFAULT 0,
    IsActive                    CHAR(1)         NOT NULL DEFAULT 'Y' CHECK (IsActive IN ('Y','N')),
    Created                     TIMESTAMP       NOT NULL DEFAULT NOW(),
    CreatedBy                   NUMERIC(10,0)   NOT NULL DEFAULT 0,
    Updated                     TIMESTAMP       NOT NULL DEFAULT NOW(),
    UpdatedBy                   NUMERIC(10,0)   NOT NULL DEFAULT 0,

    -- UUID for replication support
    TW_InvoicePrefixMap_UU      CHAR(36),

    -- Foreign Key to TW_InvoicePrefix
    TW_InvoicePrefix_ID         NUMERIC(10,0)   NOT NULL,

    -- Foreign Key to iDempiere standard invoice
    C_Invoice_ID                NUMERIC(10,0)   NOT NULL,

    -- Business Columns
    -- InvoiceNumber: Full formatted invoice number, e.g. AA0000001
    InvoiceNumber               VARCHAR(10)     NOT NULL,

    -- DateInvoiced: Date the invoice was issued; determines the bimonthly
    --               reporting period to which this invoice belongs.
    DateInvoiced                TIMESTAMP       NOT NULL,

    -- Buyer tax ID (統一編號) - mandatory for three-part invoices (三聯式)
    -- NULL allowed for two-part invoices (二聯式) where buyer has no tax ID
    BuyerTaxID                  CHAR(8),

    -- IsDateSequenceValid: 'Y' when this invoice's date is >= the previous
    --                      invoice date within the same prefix block.
    IsDateSequenceValid         CHAR(1)         NOT NULL DEFAULT 'Y'
                                    CHECK (IsDateSequenceValid IN ('Y','N')),

    -- IsMonthConsistent: 'Y' when the invoice month matches the transaction
    --                    month; 'N' when an exception has been recorded.
    IsMonthConsistent           CHAR(1)         NOT NULL DEFAULT 'Y'
                                    CHECK (IsMonthConsistent IN ('Y','N')),

    -- MonthConsistentReason: Free-text explanation when IsMonthConsistent='N'.
    MonthConsistentReason       VARCHAR(255),

    -- IsExpiryWarning: 'Y' when the input tax claim right is within 90 days
    --                  of the 10-year statutory expiry limit.
    IsExpiryWarning             CHAR(1)         NOT NULL DEFAULT 'N'
                                    CHECK (IsExpiryWarning IN ('Y','N')),

    -- Constraints
    CONSTRAINT TW_InvoicePrefixMap_Key      PRIMARY KEY (TW_InvoicePrefixMap_ID),
    CONSTRAINT TW_InvoicePrefixMap_UU_Key   UNIQUE (TW_InvoicePrefixMap_UU),
    CONSTRAINT TW_InvoicePrefixMap_Invoice  UNIQUE (C_Invoice_ID),

    -- Foreign Keys
    CONSTRAINT TW_IPM_Client    FOREIGN KEY (AD_Client_ID)
        REFERENCES AD_Client(AD_Client_ID),
    CONSTRAINT TW_IPM_Org       FOREIGN KEY (AD_Org_ID)
        REFERENCES AD_Org(AD_Org_ID),
    CONSTRAINT TW_IPM_Prefix    FOREIGN KEY (TW_InvoicePrefix_ID)
        REFERENCES TW_InvoicePrefix(TW_InvoicePrefix_ID)
);

COMMENT ON TABLE TW_Invoice_Prefix_Map IS 'Maps C_Invoice records to Taiwan invoice prefix (字軌) and stores per-invoice validation flags';
COMMENT ON COLUMN TW_Invoice_Prefix_Map.InvoiceNumber IS 'Full formatted Taiwan invoice number, e.g. AA0000001';
COMMENT ON COLUMN TW_Invoice_Prefix_Map.DateInvoiced IS 'Invoice issue date; determines the bimonthly reporting period';
COMMENT ON COLUMN TW_Invoice_Prefix_Map.IsDateSequenceValid IS 'Y when invoice date is >= previous invoice date in the same prefix block';
COMMENT ON COLUMN TW_Invoice_Prefix_Map.IsMonthConsistent IS 'Y when invoice month matches the transaction month';
COMMENT ON COLUMN TW_Invoice_Prefix_Map.MonthConsistentReason IS 'Explanation for cross-month exceptions when IsMonthConsistent=N';
COMMENT ON COLUMN TW_Invoice_Prefix_Map.IsExpiryWarning IS 'Y when the input tax claim is within 90 days of the 10-year statutory expiry';
COMMENT ON COLUMN TW_Invoice_Prefix_Map.BuyerTaxID IS 'Buyer unified business number (統一編號), required for SALES_TRIPART (三聯式) invoices';

-- ===========================================================================
-- Indexes
-- ===========================================================================

-- Primary lookup: find the map record for a given invoice
CREATE INDEX IF NOT EXISTS idx_tw_ipm_invoice
    ON TW_Invoice_Prefix_Map (C_Invoice_ID);

-- Lookup by prefix (e.g., list all invoices for a prefix block)
CREATE INDEX IF NOT EXISTS idx_tw_ipm_prefix
    ON TW_Invoice_Prefix_Map (TW_InvoicePrefix_ID, DateInvoiced);

-- Expiry warning sweep
CREATE INDEX IF NOT EXISTS idx_tw_ipm_expiry
    ON TW_Invoice_Prefix_Map (AD_Client_ID, IsExpiryWarning, DateInvoiced)
    WHERE IsExpiryWarning = 'Y';

-- Organisation + date range queries (period reporting)
CREATE INDEX IF NOT EXISTS idx_tw_ipm_org_date
    ON TW_Invoice_Prefix_Map (AD_Org_ID, DateInvoiced);
