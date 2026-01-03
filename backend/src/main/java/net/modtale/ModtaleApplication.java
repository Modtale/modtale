package net.modtale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
public class ModtaleApplication {
    public static void main(String[] args) {
        SpringApplication.run(ModtaleApplication.class, args);
    }
}