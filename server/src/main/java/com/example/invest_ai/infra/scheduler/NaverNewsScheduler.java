package com.example.invest_ai.infra.scheduler;

import com.example.invest_ai.domain.report.dto.NaverNewsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverNewsScheduler {

    private final WebClient naverNewsWebClient;

    @Scheduled(fixedDelay = 10000)
    public void fetchNaverNews() {
        naverNewsWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("query", "삼성전자")
                        .queryParam("display", 5)
                        .queryParam("sort", "date")
                        .build())
                .retrieve()
                .bodyToMono(NaverNewsResponse.class)
                .flatMapMany(response -> {
                    log.info("[네이버 뉴스] total: {}건 | display: {}건", response.total(), response.display());
                    if (response.items() == null) {
                        return Flux.empty();
                    }
                    return Flux.fromIterable(response.items());
                })
                .subscribe(item -> {
                    String title = item.getStrippedTitle();
                    String link = item.link();
                    log.info("[네이버 뉴스] 제목: {} | 링크: {}", title, link);
                });
    }
}