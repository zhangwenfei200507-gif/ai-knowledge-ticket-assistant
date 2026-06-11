package com.zwf.aisupport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class AiSupportServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiSupportServerApplication.class, args);
    }

}