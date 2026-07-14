package com.example.invest_ai.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration // 💡 "이 파일은 시스템 설정 파일이야!"라고 스프링에게 알려주는 마크
public class RabbitMqConfig {

    @Bean
    public TopicExchange investExchange() {
        return new TopicExchange("invest-network");
    }
}