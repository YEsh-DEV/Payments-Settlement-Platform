package io.github.yesh.paycore.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.github.yesh.paycore")
@EnableScheduling
public class LedgerApplication {

  public static void main(String[] args) {
    SpringApplication.run(LedgerApplication.class, args);
  }
}
