package com.utmn.chamortsev.urlparser.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI myOpenAPI() {
        Server server = new Server();
        server.setUrl("http://localhost:8080");
        server.setDescription("Сервер приложения");

        Contact contact = new Contact();
        contact.setEmail("admin@urlparser.com");
        contact.setName("URL Parser API");
        contact.setUrl("https://localhost:8080");

        License mitLicense = new License()
                .name("MIT License")
                .url("https://localhost:8080/licenses/mit/");

        Info info = new Info()
                .title("URL Parser API")
                .version("1.0.0")
                .contact(contact)
                .description("API содержит endpoints ро работе с URL parsing application")
                .license(mitLicense);

        return new OpenAPI()
                .info(info)
                .servers(List.of(server));
    }
}