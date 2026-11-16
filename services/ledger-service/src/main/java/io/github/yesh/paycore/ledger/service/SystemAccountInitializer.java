package io.github.yesh.paycore.ledger.service;

import io.github.yesh.paycore.ledger.domain.Account;
import io.github.yesh.paycore.ledger.domain.AccountType;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisions standard system accounts per Chart of Accounts (Playbook Section 3.2). Hot accounts
 * (PROVIDER_RECEIVABLE, PLATFORM_FEE_REVENUE) are configured with default 16 stripes (S2).
 */
@Component
public class SystemAccountInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SystemAccountInitializer.class);

  // Playbook Section 3.2 Chart of Accounts
  public static final String PROVIDER_RECEIVABLE = "PROVIDER_RECEIVABLE";
  public static final String BANK_CASH = "BANK_CASH";
  public static final String PAYOUT_IN_TRANSIT = "PAYOUT_IN_TRANSIT";
  public static final String PLATFORM_FEE_REVENUE = "PLATFORM_FEE_REVENUE";
  public static final String PROVIDER_FEE_EXPENSE = "PROVIDER_FEE_EXPENSE";
  public static final String WRITE_OFF_EXPENSE = "WRITE_OFF_EXPENSE";

  // Backward compatibility aliases
  public static final String USER_FUNDS_IN_TRANSIT = "USER_FUNDS_IN_TRANSIT";
  public static final String SETTLEMENT_PAYABLE = "SETTLEMENT_PAYABLE";
  public static final String SETTLEMENT_RECEIVABLE = "SETTLEMENT_RECEIVABLE";
  public static final String GATEWAY_CLEARING = "GATEWAY_CLEARING";
  public static final String DISPUTE_RESERVE = "DISPUTE_RESERVE";
  public static final String ADJUSTMENT_EXPENSE = "ADJUSTMENT_EXPENSE";

  public static final int DEFAULT_HOT_STRIPE_COUNT = 16;

  private final AccountRepository accountRepository;

  public SystemAccountInitializer(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    // Official Playbook System Accounts
    initAccount(PROVIDER_RECEIVABLE, AccountType.ASSET, true, DEFAULT_HOT_STRIPE_COUNT);
    initAccount(PLATFORM_FEE_REVENUE, AccountType.REVENUE, true, DEFAULT_HOT_STRIPE_COUNT);
    initAccount(BANK_CASH, AccountType.ASSET, false, 1);
    initAccount(PAYOUT_IN_TRANSIT, AccountType.LIABILITY, false, 1);
    initAccount(PROVIDER_FEE_EXPENSE, AccountType.EXPENSE, false, 1);
    initAccount(WRITE_OFF_EXPENSE, AccountType.EXPENSE, false, 1);

    // Aliases for backwards compatibility with legacy tests
    initAccount(USER_FUNDS_IN_TRANSIT, AccountType.ASSET, true, DEFAULT_HOT_STRIPE_COUNT);
    initAccount(SETTLEMENT_PAYABLE, AccountType.LIABILITY, false, 1);
    initAccount(SETTLEMENT_RECEIVABLE, AccountType.ASSET, false, 1);
    initAccount(GATEWAY_CLEARING, AccountType.ASSET, false, 1);
    initAccount(DISPUTE_RESERVE, AccountType.LIABILITY, false, 1);
    initAccount(ADJUSTMENT_EXPENSE, AccountType.EXPENSE, false, 1);
  }

  public Account initAccount(String code, AccountType type, boolean isHot, int stripeCount) {
    return accountRepository
        .findByCode(code)
        .orElseGet(
            () -> {
              log.info(
                  "Provisioning system account: {} ({}, isHot={}, stripes={})",
                  code,
                  type,
                  isHot,
                  stripeCount);
              Account account = new Account(code, type, isHot, stripeCount);
              return accountRepository.save(account);
            });
  }
}
