package io.github.yesh.paycore.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.github.yesh.paycore")
@EnableScheduling
public class SettlementApplication {

  public static void main(String[] args) {
    SpringApplication.run(SettlementApplication.class, args);
  }
}
