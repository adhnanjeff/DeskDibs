package com.deskdibs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * {@code @ConfigurationPropertiesScan} binds and validates {@code deskdibs.office} at startup, so
 * a bad timezone or horizon stops the application instead of quietly changing what "today" means.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DeskDibsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeskDibsApplication.class, args);
    }
}
