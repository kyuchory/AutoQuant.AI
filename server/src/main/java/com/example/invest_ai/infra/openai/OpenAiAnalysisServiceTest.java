package com.example.invest_ai.infra.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI API 연동 검증용 테스트 클래스 (파일명에 Test 명시)
 *
 * 실행 시 가상 뉴스를 OpenAI에 전송하여 감성 분석 결과를 콘솔에 출력합니다.
 * 서버 기동 시 자동 실행되며, 정상 작동 확인 후 @Component를 주석 처리하면 됩니다.
 */
@Slf4j
//@Component
public class OpenAiAnalysisServiceTest implements CommandLineRunner {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.api-url}")
    private String apiUrl;

    @Value("${openai.model}")
    private String model;

    public OpenAiAnalysisServiceTest(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public void run(String... args) {
        log.info("");
        log.info("======================================================");
        log.info("  🔍 OpenAI 감성 분석 테스트 시작");
        log.info("======================================================");
        log.info("  Model: {}", model);
        log.info("");

        // 가상 뉴스 데이터
        String newsTitle = "삼성전자, 1.4조 대규모 AI 반도체 수출 계약 체결";
        String newsSummary = "삼성전자가 미국 빅테크 기업과 1조 4000억원 규모의 AI 반도체 공급 계약을 체결했다. "
                + "이번 계약은 삼성전자의 HBM3E 메모리 반도체를 공급하는 내용으로, "
                + "내년 상반기부터 본격적인 납품이 시작될 예정이다.";
        String currentPrice = "257,250원 (전일대비 +1.08%)";

        log.info("📰 [분석 대상 뉴스]");
        log.info("   제목: {}", newsTitle);
        log.info("   요약: {}", newsSummary);
        log.info("   현재가: {}", currentPrice);
        log.info("");

        try {
            // OpenAI API 호출
            String response = callOpenAi(newsTitle, newsSummary, currentPrice);

            // 결과 파싱 및 출력
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");

            if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                String content = (String) message.get("content");

                log.info("======================================================");
                log.info("  ✅ OpenAI 분석 결과");
                log.info("======================================================");
                log.info("");
                log.info("{}", content);
                log.info("");
                log.info("======================================================");
                log.info("  🔍 OpenAI 감성 분석 테스트 완료");
                log.info("======================================================");
            }

        } catch (Exception e) {
            log.error("❌ [OpenAI API 호출 실패] {}", e.getMessage());
        }
    }

    /**
     * OpenAI Chat API를 호출하여 뉴스 감성 분석을 수행합니다.
     */
    private String callOpenAi(String title, String summary, String price) {
        WebClient webClient = webClientBuilder.baseUrl(apiUrl).build();

        String prompt = """
                당신은 주식 뉴스 분석 전문가입니다.
                아래 뉴스를 분석하여 다음 JSON 형식으로 응답해주세요:
                {
                  "score": 1~5 (1: 매우 부정, 2: 부정, 3: 중립, 4: 긍정, 5: 매우 긍정),
                  "sentiment": "GOOD/BAD/NEUTRAL",
                  "summary": "한 줄 요약 (30자 이내)"
                }

                [뉴스 제목]
                %s

                [뉴스 요약]
                %s

                [현재 주가]
                %s
                """.formatted(title, summary, price);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a helpful financial news analyst. Always respond in Korean."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.3,
                "max_tokens", 300
        );

        try {
            String response = webClient.post()
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("📡 [OpenAI API 응답 수신 완료]");
            return response;

        } catch (Exception e) {
            log.error("❌ [OpenAI API 호출 중 오류] {}", e.getMessage());
            throw new RuntimeException("OpenAI API 호출 실패", e);
        }
    }
}