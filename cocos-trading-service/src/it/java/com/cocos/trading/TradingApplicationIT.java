package com.cocos.trading;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for functional integration tests using {@link MockMvc} and shared PostgreSQL and Redis Testcontainers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@AutoConfigureMetrics
@ActiveProfiles("test")
public abstract class TradingApplicationIT {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES_CONTAINER =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    protected static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        // Ensure docker-java negotiates API >= 1.44 on Docker Engine 29+
        if (System.getProperty("api.version") == null && System.getenv("DOCKER_API_VERSION") == null) {
            System.setProperty("api.version", "1.44");
        }
        POSTGRES_CONTAINER.start();
        REDIS_CONTAINER.start();
    }

    @Autowired
    protected MockMvc mockMvc;
}
