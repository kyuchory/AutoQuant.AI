package com.example.invest_ai.infra.openai;

import com.example.invest_ai.domain.news.dto.SentimentResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI API 클라이언트 (WebClient 기반 — clinerules §4.4 준수)
 */
@Slf4j
@Component
public class OpenAiClient {

    private final WebClient webClient;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url}") String baseUrl,
            @Value("${openai.model}") String model,
            WebClient.Builder builder) {
        this.model = model;
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /** 감성 분석 — temperature=0.1 */
    public SentimentResult analyzeSentiment(String title, String description) {
        String userMessage = """
                아래 뉴스 제목과 요약을 분석하여 투자 관점에서 감성 점수를 매겨주세요.
                - sentiment: GOOD, BAD, NEUTRAL 중 하나
                - aiScore: 0(매우 부정) ~ 100(매우 긍정)
                - aiReason: 점수를 매긴 이유 (한국어, 100자 이내)

                응답은 반드시 아래 JSON 형식으로만 출력하세요:
                {"sentiment":"GOOD","aiScore":85,"aiReason":"..."}

                아래는 뉴스 요약이며 본문 전체가 아니므로, 요약에 없는 세부 수치를 임의로 생성하지 마세요.

                제목: %s
                요약: %s
                """.formatted(title, description);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "당신은 주식 뉴스 분석 전문가입니다. 항상 지정된 JSON 형식으로만 응답하세요."),
                        Map.of("role", "user", "content", userMessage)
                ),
                "temperature", 0.1,
                "response_format", Map.of("type", "json_object")
        );

        try {
            String response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return parseSentimentResponse(response);
        } catch (Exception e) {
            log.error("[OpenAI] 감성분석 실패: {}", e.getMessage());
            return SentimentResult.neutral("OpenAI 호출 실패: " + e.getMessage());
        }
    }

    /** 리포트 생성 — temperature=0.7, max_tokens=1000 */
    public String generateReport(String prompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "당신은 투자 분석 전문가입니다. 응답은 반드시 아래 JSON 형식으로만 출력하세요. 다른 설명은 절대 포함하지 마세요. 주식코드(숫자)는 절대 포함하지 마세요."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.7,
                "max_tokens", 1000,
                "response_format", Map.of("type", "json_object")
        );
        try {
            String response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("[OpenAI] 리포트 생성 실패: {}", e.getMessage());
            return "리포트 생성에 실패했습니다. 잠시 후 다시 시도해주세요.";
        }
    }

    /** 임베딩 벡터 생성 — 1536차원 */
    public float[] generateEmbedding(String text) {
        Map<String, Object> body = Map.of(
                "model", "text-embedding-ada-002",
                "input", text
        );
        try {
            String response = webClient.post()
                    .uri("/embeddings")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode root = objectMapper.readTree(response);
            JsonNode embedding = root.path("data").get(0).path("embedding");
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = embedding.get(i).floatValue();
            }
            return vector;
        } catch (Exception e) {
            log.error("[OpenAI] 임베딩 실패: {}", e.getMessage());
            return new float[1536];
        }
    }

    private SentimentResult parseSentimentResponse(String apiResponse) {
        try {
            JsonNode root = objectMapper.readTree(apiResponse);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            String cleaned = content.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("(?s)```json\\s*|```", "").trim();
            }
            return objectMapper.readValue(cleaned, SentimentResult.class);
        } catch (Exception e) {
            log.warn("[OpenAI] 응답 파싱 실패, raw={}", apiResponse);
            return SentimentResult.neutral("응답 파싱 실패");
        }
    }
}