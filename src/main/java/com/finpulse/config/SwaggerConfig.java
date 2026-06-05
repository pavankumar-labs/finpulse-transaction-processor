package com.finpulse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI finpulseOpenAPI(){
        return new OpenAPI()
                .info(new Info()
                        .title("FinPulse API")
                        .description("Financial Transaction Log Processing System")
                        .version("v1.0")
                        .contact(new Contact()
                                .name("Pavan Kumar")
                                .email("pavan.siripireddy@gmail.com"))
                );
    }
}
