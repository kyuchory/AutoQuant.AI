package com.example.invest_ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InvestAiApplication {

	public static void main(String[] args) {
		SpringApplication.run(InvestAiApplication.class, args);
	}

}
