package com.fannet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FannetApplication {

    public static void main(String[] args) {
        SpringApplication.run(FannetApplication.class, args);
    }
}
