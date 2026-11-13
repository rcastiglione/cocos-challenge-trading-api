package com.cocos.trading;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for functional integration tests using {@link MockMvc} and an isolated H2 PostgreSQL-mode database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
public abstract class TradingApplicationIT {

    @Autowired
    protected MockMvc mockMvc;
}

