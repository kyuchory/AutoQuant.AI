package com.example.invest_ai.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE_NAME = "invest-network";
    public static final String NEWS_QUEUE = "news-queue";
    public static final String NEWS_ROUTING_KEY = "news.#";
    public static final String REPORT_QUEUE = "report-queue";
    public static final String REPORT_ROUTING_KEY = "report.#";

    @Bean
    public TopicExchange investExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    /**
     * 뉴스 분석 워커용 Queue
     */
    @Bean
    public Queue newsQueue() {
        return new Queue(NEWS_QUEUE, true);
    }

    @Bean
    public Binding newsBinding() {
        return BindingBuilder.bind(newsQueue())
                .to(investExchange())
                .with(NEWS_ROUTING_KEY);
    }

    @Bean
    public Queue reportQueue() {
        return new Queue(REPORT_QUEUE, true);
    }

    @Bean
    public Binding reportBinding() {
        return BindingBuilder.bind(reportQueue())
                .to(investExchange())
                .with(REPORT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
