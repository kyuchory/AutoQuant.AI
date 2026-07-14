package com.example.invest_ai;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/api/test-infra") // 아래 메서드들의 공통 엔드포인트
public class TestController {

    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;

    public TestController(StringRedisTemplate redisTemplate, RabbitTemplate rabbitTemplate) {
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    @GetMapping("/test") // 최종 엔드포인트
    public String testInfra() {
        try {
            // 1. Redis에 진짜 데이터 넣었다가 빼보기
            redisTemplate.opsForValue().set("mykey", "Docker Redis is Live!");
            String redisResult = redisTemplate.opsForValue().get("mykey");

            // 2. RabbitMQ에 진짜 메시지 던져보기
            rabbitTemplate.convertAndSend("invest-network", "test-routing", "Hello RabbitMQ!");
            /*
            "invest-network" (Exchange - 우체통 이름):
            메시지가 가장 먼저 도착할 우체통의 이름입니다. RabbitMQ는 메시지를 저장소(Queue)로 바로 보내지 않고 무조건 이 Exchange에 먼저 집어넣습니다.
            "test-routing" (Routing Key - 우편번호/주소):
            편지봉투에 적는 우편번호나 주소 같은 것입니다. 이 키를 보고 우체통(Exchange)이 "어? test-routing이라는 주소가 적혀있네? 그럼 이 주소랑 연결된 A 저장소에 편지를 넣어줘야겠다!" 하고 판단하는 기준이 됩니다.
            "Hello RabbitMQ!" (Object - 편지 내용):
            실제로 전송할 데이터(본문)입니다. 지금은 단순한 문자열이지만, 나중에는 {"newsId": 13, "content": "삼성전자 주가 폭등..."} 같은 JSON 데이터를 객체에 담아 던지게 됩니다.
            */

            return "🟢 [Redis 결과]: " + redisResult + " | 🟢 [RabbitMQ]: 메시지 전송 성공!";
        } catch (Exception e) {
            return "🔴 인프라 연결 실패 에러 발생: " + e.getMessage();
        }
    }
}