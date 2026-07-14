package com.example.invest_ai.openai;

import com.example.invest_ai.domain.report.dto.NaverNewsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
class NewsAiIntegrationTest {

    @Autowired
    @Qualifier("naverNewsWebClient")
    private WebClient naverNewsWebClient;

    @Value("${openai.api-key}")
    private String openAiApiKey;

    @Value("${openai.api-url}")
    private String openAiApiUrl;

    @Value("${openai.model}")
    private String openAiModel;

    private final WebClient.Builder webClientBuilder = WebClient.builder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========================================================================
    // 메서드 A: 가상 뉴스 데이터로 OpenAI 감성 분석 단독 검증
    // ========================================================================
    @Test
    @DisplayName("A: 가상 뉴스 → OpenAI 감성 분석 응답 검증")
    void testAnalyzeMockNewsWithOpenAi() {
        log.info("======================================================");
        log.info("  [메서드 A] 가상 뉴스 → OpenAI 감성 분석 테스트");
        log.info("======================================================");

        // Given: 가상 뉴스 데이터 (외부 환경 영향 없음)
        String mockTitle = "삼성전자, 1.4조 대규모 AI 반도체 수출 계약 체결";
        String mockSummary = "삼성전자가 미국 빅테크 기업과 1조 4000억원 규모의 AI 반도체 공급 계약을 체결했다. "
                + "이번 계약은 삼성전자의 HBM3E 메모리 반도체를 공급하는 내용이다.";
        String mockPrice = "257,250원 (전일대비 +1.08%)";

        log.info("📰 [입력 뉴스]");
        log.info("   제목: {}", mockTitle);
        log.info("   요약: {}", mockSummary);
        log.info("   현재가: {}", mockPrice);

        // When: OpenAI API 호출
        String response = callOpenAi(mockTitle, mockSummary, mockPrice);
        assertNotNull(response, "OpenAI 응답은 null이 아니어야 합니다");

        // Then: 응답에서 content 추출
        String content = extractContentFromOpenAiResponse(response);
        assertNotNull(content, "OpenAI 응답에 content가 포함되어야 합니다");

        log.info("======================================================");
        log.info("  ✅ OpenAI 분석 결과");
        log.info("======================================================");
        log.info("");
        log.info("{}", content);
        log.info("");

        // 검증: score, sentiment, summary 키워드 포함 확인
        assertTrue(content.contains("score") || content.contains("점수"),
                "응답에 score(점수) 정보가 포함되어야 합니다");
        assertTrue(content.contains("sentiment") || content.contains("감성") || content.contains("GOOD") || content.contains("BAD"),
                "응답에 sentiment(감성) 정보가 포함되어야 합니다");
        assertTrue(content.contains("summary") || content.contains("요약"),
                "응답에 summary(요약) 정보가 포함되어야 합니다");

        log.info("✅ [메서드 A] 가상 뉴스 분석 테스트 통과");
        log.info("======================================================");
    }

    // ========================================================================
    // 메서드 B: 실제 네이버 뉴스 수집 → OpenAI 분석 통합 검증
    // ========================================================================
    @Test
    @DisplayName("B: 실제 네이버 뉴스 → OpenAI 감성 분석 통합 검증")
    void testFetchNaverNewsAndAnalyzeWithOpenAi() {
        log.info("======================================================");
        log.info("  [메서드 B] 실제 네이버 뉴스 → OpenAI 분석 통합 테스트");
        log.info("======================================================");

        // 1. 네이버 뉴스 수집 (1건)
        NaverNewsResponse newsResponse = fetchNaverNews("삼성전자");
        assertNotNull(newsResponse, "네이버 뉴스 응답은 null이 아니어야 합니다");
        assertNotNull(newsResponse.items(), "뉴스 items는 null이 아니어야 합니다");
        assertFalse(newsResponse.items().isEmpty(), "최소 1건 이상의 뉴스가 조회되어야 합니다");

        NaverNewsResponse.Item firstNews = newsResponse.items().get(0);
        String newsTitle = firstNews.getStrippedTitle();
        String newsDescription = firstNews.description();
        String newsLink = firstNews.link();

        log.info("📰 [네이버 뉴스 수집 결과]");
        log.info("   제목: {}", newsTitle);
        log.info("   요약: {}", newsDescription);
        log.info("   링크: {}", newsLink);
        log.info("   전체 건수: {}건", newsResponse.total());
        log.info("");

        // 2. 수집된 실제 뉴스를 OpenAI로 분석
        String mockPrice = "263,000원 (전일대비 +3.34%)";
        log.info("📡 [OpenAI 분석 요청 중...]");

        String response = callOpenAi(newsTitle, newsDescription, mockPrice);
        assertNotNull(response, "OpenAI 응답은 null이 아니어야 합니다");

        String content = extractContentFromOpenAiResponse(response);
        assertNotNull(content, "OpenAI 응답에 content가 포함되어야 합니다");

        log.info("======================================================");
        log.info("  ✅ 실제 뉴스 기반 OpenAI 분석 결과");
        log.info("======================================================");
        log.info("  📰 원본 뉴스: {}", newsTitle);
        log.info("");
        log.info("{}", content);
        log.info("");
        log.info("======================================================");
        log.info("✅ [메서드 B] 네이버 뉴스 + OpenAI 통합 테스트 통과");
        log.info("======================================================");
    }

    // ========================================================================
    // 네이버 뉴스 API 호출
    // ========================================================================
    private NaverNewsResponse fetchNaverNews(String query) {
        try {
            return naverNewsWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("query", query)
                            .queryParam("display", 1)
                            .queryParam("sort", "date")
                            .build())
                    .retrieve()
                    .bodyToMono(NaverNewsResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("❌ 네이버 뉴스 수집 실패: {}", e.getMessage());
            return null;
        }
    }

    // ========================================================================
    // OpenAI API 호출
    // ========================================================================
    private String callOpenAi(String title, String summary, String price) {
        WebClient webClient = webClientBuilder.baseUrl(openAiApiUrl).build();

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
                "model", openAiModel,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a helpful financial news analyst. Always respond in Korean."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.3,
                "max_tokens", 300
        );

        try {
            return webClient.post()
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.error("❌ OpenAI API 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * OpenAI 응답 JSON에서 content 필드 추출
     */
    private String extractContentFromOpenAiResponse(String response) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            }
        } catch (Exception e) {
            log.warn("OpenAI 응답 파싱 실패, raw 출력: {}", response);
        }
        return response;
    }
}