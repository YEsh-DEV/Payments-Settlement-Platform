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

@Component
public class SystemAccountInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SystemAccountInitializer.class);

  public static final String USER_FUNDS_IN_TRANSIT = "USER_FUNDS_IN_TRANSIT";
  public static final String PLATFORM_FEE_REVENUE = "PLATFORM_FEE_REVENUE";
  public static final String SETTLEMENT_PAYABLE = "SETTLEMENT_PAYABLE";
  public static final String SETTLEMENT_RECEIVABLE = "SETTLEMENT_RECEIVABLE";
  public static final String GATEWAY_CLEARING = "GATEWAY_CLEARING";
  public static final String DISPUTE_RESERVE = "DISPUTE_RESERVE";
  public static final String ADJUSTMENT_EXPENSE = "ADJUSTMENT_EXPENSE";

  private final AccountRepository accountRepository;

  public SystemAccountInitializer(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    initAccount(USER_FUNDS_IN_TRANSIT, AccountType.ASSET, true, 10);
    initAccount(PLATFORM_FEE_REVENUE, AccountType.REVENUE, true, 10);
    initAccount(SETTLEMENT_PAYABLE, AccountType.LIABILITY, true, 10);
    initAccount(SETTLEMENT_RECEIVABLE, AccountType.ASSET, true, 10);
    initAccount(GATEWAY_CLEARING, AccountType.ASSET, true, 10);
    initAccount(DISPUTE_RESERVE, AccountType.LIABILITY, false, 1);
    initAccount(ADJUSTMENT_EXPENSE, AccountType.EXPENSE, false, 1);
  }

  public Account initAccount(String code, AccountType type, boolean isHot, int stripeCount) {
    return accountRepository
        .findByCode(code)
        .orElseGet(
            () -> {
              log.info("Provisioning system account: {} ({})", code, type);
              Account account = new Account(code, type, isHot, stripeCount);
              return accountRepository.save(account);
            });
  }
}
