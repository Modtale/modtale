package net.modtale.status;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "net.modtale.status")
@ConfigurationPropertiesScan(basePackages = "net.modtale.status")
@EnableScheduling
public class StatusServiceApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(StatusServiceApplication.class);
        app.setAdditionalProfiles("status");
        app.run(args);
    }
}
