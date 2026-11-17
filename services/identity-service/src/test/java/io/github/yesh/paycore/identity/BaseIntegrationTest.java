package io.github.yesh.paycore.identity;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests running against PostgreSQL and Redis.
 *
 * <p>Uses Testcontainers by default in CI and standard Docker environments. If the system property
 * {@code paycore.test.use-host-services=true} is explicitly set, it connects to host services on
 * localhost:5432 and localhost:6379.
 */
public abstract class BaseIntegrationTest {

  private static final boolean USE_HOST_SERVICES =
      Boolean.getBoolean("paycore.test.use-host-services");

  private static PostgreSQLContainer<?> postgres;
  private static GenericContainer<?> redis;

  static {
    if (System.getProperty("DOCKER_API_VERSION") == null
        && System.getenv("DOCKER_API_VERSION") == null) {
      System.setProperty("DOCKER_API_VERSION", "1.45");
    }

    if (USE_HOST_SERVICES) {
      System.err.println(
          "\n================================================================================\n"
              + " [WARNING] EXPLICIT HOST SERVICES MODE: Testing against host PostgreSQL (5432) & Redis (6379)\n"
              + " Configuration: -Dpaycore.test.use-host-services=true\n"
              + "================================================================================\n");
      System.setProperty("paycore.test.environment", "host-services");
    } else {
      try {
        postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("identity")
                .withUsername("identity_user")
                .withPassword("identity_dev_password");
        postgres.start();

        redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        redis.start();

        System.setProperty("paycore.test.environment", "testcontainers");
      } catch (Throwable t) {
        throw new IllegalStateException(
            "Docker is unreachable and Testcontainers could not start: "
                + t.getMessage()
                + ". If you explicitly intend to test against host PostgreSQL/Redis, "
                + "pass -Dpaycore.test.use-host-services=true.",
            t);
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
    if (redis != null && redis.isRunning()) {
      registry.add("spring.data.redis.host", redis::getHost);
      registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
  }
}
