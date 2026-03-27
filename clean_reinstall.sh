#!/usr/bin/env bash
# clean_reinstall.sh — Remove all TW_* dictionary and data, then redeploy fresh.
# Usage: ./clean_reinstall.sh
#
# What it does:
#   1. Removes all TW-related dictionary entries from iDempiere DB (correct FK order)
#   2. Drops physical TW_* tables
#   3. Clears ad_package_imp history so 2Pack reinstalls from scratch
#   4. Builds and deploys the new bundle (calls deploy.sh)

set -e

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-idempiere}"
DB_USER="${DB_USER:-adempiere}"
export PGPASSWORD="${PGPASSWORD:-adempiere}"

PG="psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME"

run_sql() {
    local label="$1"
    local sql="$2"
    echo -n "  $label ... "
    local rows
    rows=$($PG -tAc "$sql" 2>&1)
    echo "done ($rows)"
}

echo ""
echo "=== STEP 1: Remove TW dictionary (FK order) ==="

run_sql "AD_Window_Access"    "DELETE FROM AD_Window_Access WHERE AD_Window_ID IN (SELECT AD_Window_ID FROM AD_Window WHERE EntityType='TW'); SELECT count(*) FROM AD_Window WHERE EntityType='TW'"
run_sql "AD_Process_Access"   "DELETE FROM AD_Process_Access WHERE AD_Process_ID IN (SELECT AD_Process_ID FROM AD_Process WHERE EntityType='TW'); SELECT 0"
run_sql "AD_Field (Trl)"      "DELETE FROM AD_Field_Trl WHERE AD_Field_ID IN (SELECT f.AD_Field_ID FROM AD_Field f JOIN AD_Tab t ON f.AD_Tab_ID=t.AD_Tab_ID JOIN AD_Window w ON t.AD_Window_ID=w.AD_Window_ID WHERE w.EntityType='TW'); SELECT 0"
run_sql "AD_Field"            "DELETE FROM AD_Field WHERE AD_Tab_ID IN (SELECT t.AD_Tab_ID FROM AD_Tab t JOIN AD_Window w ON t.AD_Window_ID=w.AD_Window_ID WHERE w.EntityType='TW'); SELECT 0"
run_sql "AD_Tab (Trl)"        "DELETE FROM AD_Tab_Trl WHERE AD_Tab_ID IN (SELECT t.AD_Tab_ID FROM AD_Tab t JOIN AD_Window w ON t.AD_Window_ID=w.AD_Window_ID WHERE w.EntityType='TW'); SELECT 0"
run_sql "AD_Tab"              "DELETE FROM AD_Tab WHERE AD_Window_ID IN (SELECT AD_Window_ID FROM AD_Window WHERE EntityType='TW'); SELECT 0"
run_sql "AD_Menu (process)"   "DELETE FROM AD_Menu WHERE EntityType='TW' AND Action='P'; SELECT 0"
run_sql "AD_Menu (window)"    "DELETE FROM AD_Menu WHERE EntityType='TW' AND Action='W'; SELECT 0"
run_sql "AD_Menu (summary)"   "DELETE FROM AD_Menu WHERE EntityType='TW' AND IsSummary='Y'; SELECT 0"
run_sql "AD_Window (Trl)"     "DELETE FROM AD_Window_Trl WHERE AD_Window_ID IN (SELECT AD_Window_ID FROM AD_Window WHERE EntityType='TW'); SELECT 0"
run_sql "AD_Window"           "DELETE FROM AD_Window WHERE EntityType='TW'; SELECT 0"
run_sql "AD_Process_Para"     "DELETE FROM AD_Process_Para WHERE AD_Process_ID IN (SELECT AD_Process_ID FROM AD_Process WHERE EntityType='TW'); SELECT 0"
run_sql "AD_Process"          "DELETE FROM AD_Process WHERE EntityType='TW'; SELECT 0"

echo ""
echo "=== STEP 2: Drop physical TW_* tables ==="
# NOTE: iDempiere creates tables in the 'adempiere' schema (not 'public')
run_sql "TW_InvoiceAdjustment"  "DROP TABLE IF EXISTS adempiere.TW_InvoiceAdjustment; SELECT 0"
run_sql "TW_Invoice_Prefix_Map" "DROP TABLE IF EXISTS adempiere.TW_Invoice_Prefix_Map; SELECT 0"
run_sql "TW_TaxStatement"       "DROP TABLE IF EXISTS adempiere.TW_TaxStatement; SELECT 0"
run_sql "TW_InvoicePrefix"      "DROP TABLE IF EXISTS adempiere.TW_InvoicePrefix; SELECT 0"

echo ""
echo "=== STEP 3: Remove AD_Column, AD_Table, AD_Sequence, AD_Element, AD_Reference ==="
run_sql "AD_Column"          "DELETE FROM AD_Column WHERE AD_Table_ID IN (SELECT AD_Table_ID FROM AD_Table WHERE EntityType='TW'); SELECT 0"
run_sql "AD_Table (Trl)"     "DELETE FROM AD_Table_Trl WHERE AD_Table_ID IN (SELECT AD_Table_ID FROM AD_Table WHERE EntityType='TW'); SELECT 0"
run_sql "AD_Table"           "DELETE FROM AD_Table WHERE EntityType='TW'; SELECT 0"
run_sql "AD_Sequence"        "DELETE FROM AD_Sequence WHERE Name LIKE 'TW_%'; SELECT 0"
run_sql "AD_Element (Trl)"   "DELETE FROM AD_Element_Trl WHERE AD_Element_ID IN (SELECT AD_Element_ID FROM AD_Element WHERE EntityType='TW'); SELECT 0"
run_sql "AD_Element"         "DELETE FROM AD_Element WHERE EntityType='TW'; SELECT 0"
run_sql "AD_Ref_List"        "DELETE FROM AD_Ref_List WHERE AD_Reference_ID IN (SELECT AD_Reference_ID FROM AD_Reference WHERE Name LIKE 'TW_%'); SELECT 0"
run_sql "AD_Reference"       "DELETE FROM AD_Reference WHERE Name LIKE 'TW_%'; SELECT 0"
run_sql "AD_EntityType"      "DELETE FROM AD_EntityType WHERE EntityType='TW'; SELECT 0"

echo ""
echo "=== STEP 4: Clear 2Pack install history ==="
run_sql "AD_Package_Imp_Detail" "DELETE FROM AD_Package_Imp_Detail WHERE AD_Package_Imp_ID IN (SELECT AD_Package_Imp_ID FROM AD_Package_Imp WHERE Name LIKE '%TW%' OR PK_Version LIKE '%1.0.%'); SELECT 0"
run_sql "AD_Package_Imp"        "DELETE FROM AD_Package_Imp WHERE Name LIKE '%TW%' OR PK_Version LIKE '%1.0.%'; SELECT 0"

echo ""
echo "=== STEP 5: Build and deploy ==="
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"
./deploy.sh

echo ""
echo "=== Done. Please re-login to iDempiere to see new permissions. ==="
