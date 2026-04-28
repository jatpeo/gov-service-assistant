package com.gov.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class GovService_Alibaba_AssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(GovService_Alibaba_AssistantApplication.class, args);
    }
}
