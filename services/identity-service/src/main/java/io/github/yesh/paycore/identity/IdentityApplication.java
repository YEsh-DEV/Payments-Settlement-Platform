package io.github.yesh.paycore.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.github.yesh.paycore")
public class IdentityApplication {

  public static void main(String[] args) {
    SpringApplication.run(IdentityApplication.class, args);
  }
}
