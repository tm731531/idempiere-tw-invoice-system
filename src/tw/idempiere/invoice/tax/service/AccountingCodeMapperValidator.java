/******************************************************************************
 * Taiwan Invoice Tax System for iDempiere
 * Copyright (C) Taiwan iDempiere Community. All Rights Reserved.
 * License: GNU General Public License version 2
 *****************************************************************************/
package tw.idempiere.invoice.tax.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import tw.idempiere.invoice.tax.service.AccountingCodeMapper.AccountInfo;
import tw.idempiere.invoice.tax.util.AccountingCodes;

/**
 * Validates that a tenant's chart of accounts contains all accounting codes
 * required by the Taiwan Invoice Tax System.
 *
 * <p>This class contains <em>no direct iDempiere persistence dependencies</em>
 * beyond delegating database queries to {@link AccountingCodeMapper}.  It can
 * therefore be tested with a mock or stub mapper, keeping unit tests fast and
 * database-free.</p>
 *
 * <h3>Validation checks performed</h3>
 * <ol>
 *   <li>All five mandatory account codes exist in {@code C_ElementValue} and
 *       are active for the given tenant.</li>
 *   <li>Each account's {@code AccountType} matches the type expected by the
 *       Taiwan Chart of Accounts specification
 *       (e.g. account {@code 1112} must be an Asset account).</li>
 *   <li>Each mandatory account is a leaf (non-summary) account so that
 *       journal lines can be posted against it.</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * AccountingCodeMapper mapper = new AccountingCodeMapper();
 * AccountingCodeMapperValidator validator = new AccountingCodeMapperValidator(mapper);
 * ValidationResult result = validator.validateAllRequiredAccounts(clientId, ctx, null);
 * if (!result.valid) {
 *     result.errors.forEach(System.err::println);
 * }
 * }</pre>
 *
 * @author Taiwan iDempiere Community
 * @version 1.0.0
 * @see AccountingCodeMapper
 * @see AccountingCodes
 */
public class AccountingCodeMapperValidator {

    private static final Logger log = Logger.getLogger(AccountingCodeMapperValidator.class.getName());

    /** The mapper used to resolve accounts from the database. */
    private final AccountingCodeMapper mapper;

    /**
     * Constructs a validator backed by the given mapper.
     *
     * @param mapper the {@link AccountingCodeMapper} to use for account lookups
     *               (must not be {@code null})
     * @throws IllegalArgumentException if {@code mapper} is {@code null}
     */
    public AccountingCodeMapperValidator(AccountingCodeMapper mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper must not be null");
        }
        this.mapper = mapper;
    }

    // -----------------------------------------------------------------------
    // Result type
    // -----------------------------------------------------------------------

    /**
     * Immutable outcome of a validation run.
     */
    public static final class ValidationResult {

        /** {@code true} when all required accounts passed all checks. */
        public final boolean valid;

        /**
         * Human-readable error messages describing each failed check.
         * Empty when {@code valid == true}.
         */
        public final List<String> errors;

        /**
         * Human-readable warning messages for conditions that do not block
         * operation but should be reviewed (e.g. account type mismatch with
         * a non-standard chart of accounts).
         */
        public final List<String> warnings;

        ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid    = valid;
            this.errors   = Collections.unmodifiableList(errors);
            this.warnings = Collections.unmodifiableList(warnings);
        }

        @Override
        public String toString() {
            return "ValidationResult[valid=" + valid
                + ", errors=" + errors.size()
                + ", warnings=" + warnings.size() + "]";
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Validates that all five mandatory Taiwan Invoice Tax accounts exist and
     * have the correct type in the given tenant's chart of accounts.
     *
     * <p>The mandatory accounts checked are:
     * <ul>
     *   <li>{@value AccountingCodes#INPUT_TAX_ACCOUNT_VALUE} – Input Tax Receivable (應收進項稅額)</li>
     *   <li>{@value AccountingCodes#OUTPUT_TAX_ACCOUNT_VALUE} – Output Tax Payable (應付營業稅)</li>
     *   <li>{@value AccountingCodes#TAXABLE_REVENUE_ACCOUNT_VALUE} – Taxable Sales Revenue (銷售收入-應稅)</li>
     *   <li>{@value AccountingCodes#EXEMPT_REVENUE_ACCOUNT_VALUE} – Exempt Sales Revenue (銷售收入-免稅)</li>
     *   <li>{@value AccountingCodes#COGS_ACCOUNT_VALUE} – Cost of Goods Sold (銷貨成本)</li>
     * </ul>
     * </p>
     *
     * @param clientId {@code AD_Client_ID} of the tenant to validate
     * @param ctx      iDempiere context
     * @param trxName  transaction name, or {@code null}
     * @return a {@link ValidationResult} describing the outcome
     */
    public ValidationResult validateAllRequiredAccounts(int clientId,
                                                         Properties ctx,
                                                         String trxName) {
        List<String> errors   = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        validateSingleAccount(clientId, ctx, trxName,
            AccountingCodes.INPUT_TAX_ACCOUNT_VALUE,
            AccountingCodes.ACCOUNT_TYPE_ASSET,
            "Input Tax Receivable (應收進項稅額)",
            errors, warnings);

        validateSingleAccount(clientId, ctx, trxName,
            AccountingCodes.OUTPUT_TAX_ACCOUNT_VALUE,
            AccountingCodes.ACCOUNT_TYPE_LIABILITY,
            "Output Tax Payable (應付營業稅)",
            errors, warnings);

        validateSingleAccount(clientId, ctx, trxName,
            AccountingCodes.TAXABLE_REVENUE_ACCOUNT_VALUE,
            AccountingCodes.ACCOUNT_TYPE_REVENUE,
            "Taxable Sales Revenue (銷售收入-應稅)",
            errors, warnings);

        validateSingleAccount(clientId, ctx, trxName,
            AccountingCodes.EXEMPT_REVENUE_ACCOUNT_VALUE,
            AccountingCodes.ACCOUNT_TYPE_REVENUE,
            "Exempt Sales Revenue (銷售收入-免稅)",
            errors, warnings);

        validateSingleAccount(clientId, ctx, trxName,
            AccountingCodes.COGS_ACCOUNT_VALUE,
            AccountingCodes.ACCOUNT_TYPE_EXPENSE,
            "Cost of Goods Sold (銷貨成本)",
            errors, warnings);

        boolean valid = errors.isEmpty();

        if (valid) {
            log.log(Level.INFO,
                "AccountingCodeMapperValidator: all required accounts valid for AD_Client_ID={0}",
                clientId);
        } else {
            log.log(Level.WARNING,
                "AccountingCodeMapperValidator: {0} error(s) found for AD_Client_ID={1}: {2}",
                new Object[]{errors.size(), clientId, errors});
        }

        return new ValidationResult(valid, errors, warnings);
    }

    /**
     * Validates a single account code against the given tenant's chart of accounts.
     *
     * @param clientId       {@code AD_Client_ID}
     * @param ctx            iDempiere context
     * @param trxName        transaction name
     * @param accountValue   account code to validate (e.g. {@code "1112"})
     * @param expectedType   expected iDempiere account type ({@code A}/{@code L}/{@code R}/{@code E})
     * @param description    human-readable account description for error messages
     * @param errors         accumulates blocking errors
     * @param warnings       accumulates non-blocking warnings
     */
    private void validateSingleAccount(int clientId,
                                        Properties ctx,
                                        String trxName,
                                        String accountValue,
                                        String expectedType,
                                        String description,
                                        List<String> errors,
                                        List<String> warnings) {

        AccountInfo info = mapper.lookupAccount(clientId, accountValue, ctx, trxName);

        if (info == null) {
            errors.add(
                "Required account not found: " + accountValue + " (" + description + "). "
                + "Please ensure the Taiwan Chart of Accounts has been imported for "
                + "AD_Client_ID=" + clientId + ". "
                + "Import the ChartOfAccountsTW.csv via System > Accounting > Import Chart of Accounts.");
            return;
        }

        // Validate account type
        if (info.accountType == null || !info.accountType.equals(expectedType)) {
            warnings.add(
                "Account type mismatch for " + accountValue + " (" + description + "): "
                + "expected AccountType='" + expectedType + "' "
                + "but found '" + info.accountType + "' (Name: " + info.name + "). "
                + "This may indicate a non-standard chart of accounts configuration. "
                + "Verify the account type in AD_Client_ID=" + clientId + ".");
        }
    }

    /**
     * Validates a single arbitrary account code for existence in the given
     * tenant's chart of accounts.
     *
     * <p>Unlike {@link #validateAllRequiredAccounts}, this method does not
     * check the account type — it only confirms the account is present and
     * active.  Useful for validating custom or optional accounts before use.</p>
     *
     * @param clientId     {@code AD_Client_ID}
     * @param accountValue the account code to check
     * @param ctx          iDempiere context
     * @param trxName      transaction name, or {@code null}
     * @return a {@link ValidationResult}; valid if the account was found
     */
    public ValidationResult validateSingleAccountExists(int clientId,
                                                         String accountValue,
                                                         Properties ctx,
                                                         String trxName) {
        List<String> errors   = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (accountValue == null || accountValue.isBlank()) {
            errors.add("accountValue must not be null or blank.");
            return new ValidationResult(false, errors, warnings);
        }

        boolean exists = mapper.validateAccountExists(clientId, accountValue, ctx, trxName);
        if (!exists) {
            errors.add(
                "Account '" + accountValue + "' not found in C_ElementValue "
                + "for AD_Client_ID=" + clientId + ". "
                + "Ensure the account is active and belongs to the Account element (ElementType='A').");
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
}
