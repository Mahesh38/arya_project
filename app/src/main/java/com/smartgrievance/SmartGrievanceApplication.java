package com.smartgrievance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartGrievanceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartGrievanceApplication.class, args);
    }
}
