package com.cocos.trading.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for OpenAPI / Swagger UI documentation and Jackson Problem modules.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tradingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cocos Capital Trading Engine API")
                        .version("1.0.0")
                        .description("Transactional Trading Engine API for Portfolio Valuation, Instrument Search, and Order Routing")
                        .contact(new Contact().name("Cocos Engineering")));
    }
}

