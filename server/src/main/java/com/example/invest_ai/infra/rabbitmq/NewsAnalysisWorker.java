package com.example.invest_ai.infra.rabbitmq;

import com.example.invest_ai.config.RabbitMqConfig;
import com.example.invest_ai.domain.news.dto.SentimentResult;
import com.example.invest_ai.domain.news.entity.NewsSentiment;
import com.example.invest_ai.domain.news.repository.NewsSentimentRepository;
import com.example.invest_ai.domain.report.dto.NaverNewsResponse;
import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.infra.config.RedisKeys;
import com.example.invest_ai.infra.openai.OpenAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 뉴스 분석 Consumer (workflow.md §5, §6)
 *
 * RabbitMQ news-queue에서 메시지를 소비하여
 * OpenAI 감성 분석 + 임베딩 후 news_sentiments에 저장하고
 * Redis report 캐시를 무효화한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsAnalysisWorker {

    private final NewsSentimentRepository newsSentimentRepository;
    private final StockRepository stockRepository;
    private final OpenAiClient openAiClient;
    private final RedisTemplate<String, String> redisTemplate;

    @RabbitListener(queues = RabbitMqConfig.NEWS_QUEUE)
    public void handleNews(NewsAnalysisMessage message) {
        String stockCode = message.stockCode();
        NaverNewsResponse.Item item = message.item();

        String newsUrl = item.link();
        String title = item.getStrippedTitle();
        String description = item.getStrippedDescription();

        // 1. 중복 체크 (uk_news_url 제약조건 대비 애플리케이션 사전 체크)
        if (newsSentimentRepository.existsByNewsUrl(newsUrl)) {
            log.debug("[뉴스 워커] 중복 뉴스 Skip: url={}", newsUrl);
            return;
        }

        // 2. Stock 엔티티 조회
        Stock stock = stockRepository.findById(stockCode).orElse(null);
        if (stock == null) {
            log.warn("[뉴스 워커] 존재하지 않는 종목: stockCode={}", stockCode);
            return;
        }

        try {
            // 3. OpenAI 감성 분석 (GOOD/BAD/NEUTRAL + 0~100 점수)
            SentimentResult sentiment = openAiClient.analyzeSentiment(title, description);
            log.info("[뉴스 워커] 감성분석 완료: stock={} sentiment={} score={}",
                    stockCode, sentiment.sentiment(), sentiment.aiScore());

            // 4. OpenAI 임베딩 생성 (float[1536])
            float[] embedding = openAiClient.generateEmbedding(description);

            // 5. pubDate 파싱 (RFC 1123 → LocalDateTime)
            LocalDateTime publishedAt = parsePubDate(item.pubDate());

            // 6. news_sentiments INSERT
            NewsSentiment newsSentiment = NewsSentiment.builder()
                    .stock(stock)
                    .newsUrl(newsUrl)
                    .title(title)
                    .contentSummary(description)
                    .sentiment(sentiment.sentiment())
                    .aiScore(sentiment.aiScore())
                    .aiReason(sentiment.aiReason())
                    .publishedAt(publishedAt)
                    .embedding(embedding)
                    .build();
            newsSentimentRepository.save(newsSentiment);
            log.info("[뉴스 워커] 저장 완료: stock={} title={}", stockCode, title);

            // 7. Redis report 캐시 무효화 (redisflow.md §3)
            String cacheKey = RedisKeys.reportText(stockCode);
            redisTemplate.delete(cacheKey);
            log.info("[뉴스 워커] 캐시 무효화: key={}", cacheKey);

        } catch (Exception e) {
            // 예외 발생 시 로그만 남기고 Skip
            // (재처리 시 uk_news_url UNIQUE 제약으로 자연 방어)
            log.error("[뉴스 워커] 분석 실패: stock={} url={} error={}",
                    stockCode, newsUrl, e.getMessage());
        }
    }

    /**
     * 네이버 뉴스 API의 pubDate(RFC 1123 형식)를 LocalDateTime으로 변환한다.
     */
    private LocalDateTime parsePubDate(String pubDate) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
            return ZonedDateTime.parse(pubDate, formatter).toLocalDateTime();
        } catch (Exception e) {
            log.warn("[뉴스 워커] pubDate 파싱 실패, 현재 시각으로 대체: {}", pubDate);
            return LocalDateTime.now();
        }
    }
}