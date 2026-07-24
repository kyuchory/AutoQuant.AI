package com.example.invest_ai.infra.naver;

import com.example.invest_ai.domain.report.dto.NaverNewsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * 네이버 뉴스 검색 API 클라이언트 (clinerules §4.4)
 *
 * stocks.is_monitored = TRUE인 종목명을 키워드로 네이버 뉴스 검색 API를 호출한다.
 * 응답 필드는 title, description(요약), link, pubDate로 구성된다.
 * Service 레이어에 네이버 API 세부 포맷을 노출하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NaverNewsClient {

    private final WebClient naverNewsWebClient;

    /**
     * 주어진 검색어로 네이버 뉴스 검색 API를 호출하여 뉴스 아이템 목록을 반환한다.
     *
     * @param query 검색 키워드 (종목명)
     * @return 뉴스 아이템 리스트 (실패 시 빈 리스트)
     */
    public List<NaverNewsResponse.Item> fetchNews(String query) {
        try {
            NaverNewsResponse response = naverNewsWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("query", query)
                            .queryParam("display", 5)
                            .queryParam("sort", "date")
                            .build())
                    .retrieve()
                    .bodyToMono(NaverNewsResponse.class)
                    .block();

            if (response == null || response.items() == null) {
                log.debug("[네이버 뉴스] query={} 결과 없음", query);
                return List.of();
            }

            log.debug("[네이버 뉴스] query={} total={} display={}", query, response.total(), response.display());
            return response.items();
        } catch (Exception e) {
            log.error("[네이버 뉴스] query={} 수집 실패: {}", query, e.getMessage());
            return List.of();
        }
    }
}