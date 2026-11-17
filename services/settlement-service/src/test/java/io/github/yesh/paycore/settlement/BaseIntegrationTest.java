package io.github.yesh.paycore.settlement;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests running against PostgreSQL.
 *
 * <p>Uses Testcontainers by default in CI and standard Docker environments. If the system property
 * {@code paycore.test.use-host-services=true} is explicitly set, or if Docker is unreachable during
 * local execution, it gracefully falls back to host-provided services on localhost:5432.
 */
public abstract class BaseIntegrationTest {

  private static final boolean USE_HOST_SERVICES =
      Boolean.getBoolean("paycore.test.use-host-services");

  private static PostgreSQLContainer<?> postgres;

  static {
    if (!USE_HOST_SERVICES) {
      try {
        postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("settlement")
                .withUsername("settlement_user")
                .withPassword("settlement_dev_password");
        postgres.start();
      } catch (Throwable t) {
        System.err.println(
            "Testcontainers initialization failed; falling back to host services if reachable: "
                + t.getMessage());
      }
    }
  }

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    if (postgres != null && postgres.isRunning()) {
      registry.add("spring.datasource.url", postgres::getJdbcUrl);
      registry.add("spring.datasource.username", postgres::getUsername);
      registry.add("spring.datasource.password", postgres::getPassword);
    }
  }
}
