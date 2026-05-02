package com.candleservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Candlestick Market Data API")
                        .version("1.0.0")
                        .description("""
                                Real-time OHLC candlestick aggregation service.

                                Ingests bid/ask market data via Kafka and aggregates
                                into candlestick format for multiple symbols and intervals.

                                **Dev API Key:** `dev-key-abc123`
                                """)
                        .contact(new Contact()
                                .name("Candle Service")
                                .email("dev@candleservice.com"))
                )
                .addSecurityItem(
                        new SecurityRequirement().addList("ApiKeyAuth")
                )
                .components(new Components()
                        .addSecuritySchemes("ApiKeyAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-API-Key")
                                        .description("API key. Use dev-key-abc123 for testing.")
                        )
                );
    }
}