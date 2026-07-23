package com.example.invest_ai.infra.rabbitmq;

import com.example.invest_ai.domain.news.entity.NewsSentiment;
import com.example.invest_ai.domain.news.repository.NewsSentimentRepository;
import com.example.invest_ai.domain.report.entity.AiInvestmentReport;
import com.example.invest_ai.domain.report.repository.AiInvestmentReportRepository;
import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.domain.user.entity.User;
import com.example.invest_ai.domain.user.repository.UserRepository;
import com.example.invest_ai.infra.config.RedisKeys;
import com.example.invest_ai.infra.converter.FloatArrayToByteArrayConverter;
import com.example.invest_ai.infra.openai.OpenAiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 리포트 생성 Consumer (workflow.md §8)
 *
 * RabbitMQ report-queue에서 메시지를 소비하여
 * RAG(Retrieval-Augmented Generation) 파이프라인으로 리포트를 생성한다.
 * 벡터 유사도는 자바에서 직접 코사인 유사도로 계산한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportWorker {

    private final NewsSentimentRepository newsSentimentRepository;
    private final AiInvestmentReportRepository reportRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final OpenAiClient openAiClient;
    private final RedisTemplate<String, String> redisTemplate;

    @RabbitListener(queues = "report-queue")
    public void handleReport(ReportMessage message) {
        String stockCode = message.stockCode();
        Long userId = message.userId();

        Stock stock = stockRepository.findById(stockCode).orElse(null);
        if (stock == null) {
            log.warn("[리포트 워커] 존재하지 않는 종목: {}", stockCode);
            return;
        }

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;

        try {
            // ① R: 24시간 이내 뉴스 조회 (DB에서 embedding 포함 모든 컬럼)
            LocalDateTime since = LocalDateTime.now().minusHours(24);
            List<NewsSentiment> recentNews = newsSentimentRepository
                    .findRecentNewsByStockCode(stockCode, since);

            // ② 자바에서 코사인 유사도 계산 → 상위 10건 추출
            float[] queryEmbedding = openAiClient.generateEmbedding(stock.getStockName());
            List<NewsSentiment> relatedNews = recentNews.stream()
                    .map(n -> new NewsWithScore(n, cosineSimilarity(
                            queryEmbedding,
                            n.getEmbedding())))
                    .filter(ws -> !Float.isNaN(ws.score))
                    .sorted(Comparator.comparingDouble(NewsWithScore::score).reversed())
                    .limit(10)
                    .map(NewsWithScore::news)
                    .collect(Collectors.toList());

            // ③ A: 프롬프트 조립 — 통계 계산
            String newsContext = relatedNews.isEmpty()
                    ? "최근 24시간 내 관련 뉴스가 없습니다."
                    : relatedNews.stream()
                            .map(n -> String.format("- [%s] %s (AI 점수: %d)\n  요약: %s",
                                    n.getSentiment(), n.getTitle(), n.getAiScore(),
                                    n.getContentSummary()))
                            .collect(Collectors.joining("\n"));

            // Java에서 직접 감성 통계 계산
            double avgScore = relatedNews.isEmpty() ? 0
                    : relatedNews.stream().mapToInt(NewsSentiment::getAiScore).average().orElse(0);
            int roundedAvg = (int) Math.round(avgScore);
            long goodCount = relatedNews.stream()
                    .filter(n -> "GOOD".equals(n.getSentiment())).count();
            long badCount = relatedNews.stream()
                    .filter(n -> "BAD".equals(n.getSentiment())).count();
            long neutralCount = relatedNews.stream()
                    .filter(n -> "NEUTRAL".equals(n.getSentiment())).count();

            String prompt = String.format("""
                    당신은 투자 분석 전문가입니다.
                    아래 종목의 최근 24시간 뉴스 요약을 바탕으로 투자 리포트를 JSON 형식으로 작성해주세요.

                    종목명: %s
                    (※ 주의: 응답에 주식코드(%s)를 절대 포함하지 마세요. 제목에는 종목명만 사용하세요.)

                    최근 주요 뉴스 (24시간 이내):
                    %s

                    아래 JSON 형식으로만 응답하세요. 다른 설명은 절대 포함하지 마세요.
                    {
                      "title": "종목명 투자 리포트",
                      "recent": "24시간 내 주요 뉴스 요약 (600자 이내)",
                      "opinion": "감성 분포와 AI 점수를 고려한 종합 투자 의견 (500자 이내)",
                      "avgScore": %d,
                      "good": %d,
                      "bad": %d,
                      "neutral": %d
                    }

                    - title은 반드시 "종목명 투자 리포트" 형식으로 작성하세요 (주식코드 제외).
                    - recent와 opinion은 한국어로 작성하세요.
                    - avgScore, good, bad, neutral은 위에 제시된 정확한 숫자를 그대로 사용하세요.
                    - 아래는 뉴스 요약이며 본문 전체가 아니므로, 요약에 없는 세부 수치를 임의로 생성하지 마세요.
                    """, stock.getStockName(), stockCode, newsContext, roundedAvg, goodCount, badCount, neutralCount);

            // ④ G: OpenAI Chat API → 리포트 생성
            String reportContent = openAiClient.generateReport(prompt);

            // JSON에 createdAt 필드 추가 (Redis/DB 일관성 확보)
            try {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode rootNode = (ObjectNode) mapper.readTree(reportContent);
                rootNode.put("createdAt", LocalDateTime.now().toString());
                reportContent = mapper.writeValueAsString(rootNode);
            } catch (Exception e) {
                log.warn("[리포트 워커] createdAt 주입 실패, 원본 유지: {}", e.getMessage());
            }

            // ⑤ ai_investment_reports 저장
            AiInvestmentReport report = AiInvestmentReport.builder()
                    .user(user)
                    .stock(stock)
                    .reportContent(reportContent)
                    .build();
            reportRepository.save(report);
            log.info("[리포트 워커] 저장 완료: stock={}", stockCode);

            // ⑥ Redis 캐싱 — TTL 12시간
            String cacheKey = RedisKeys.reportText(stockCode);
            redisTemplate.opsForValue().set(cacheKey, reportContent, Duration.ofHours(12));
            log.info("[리포트 워커] Redis 캐싱 완료: {}", cacheKey);

        } catch (Exception e) {
            log.error("[리포트 워커] 리포트 생성 실패: stock={} error={}", stockCode, e.getMessage());
        }
    }

    /** 코사인 유사도 계산 (자바 직접 구현) */
    private static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return 0f;
        }
        float dot = 0f, normA = 0f, normB = 0f;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        float denominator = (float) (Math.sqrt(normA) * Math.sqrt(normB));
        return denominator == 0f ? 0f : dot / denominator;
    }

    /** 뉴스 + 유사도 점수를 임시로 묶는 레코드 */
    private record NewsWithScore(NewsSentiment news, float score) {
    }
}