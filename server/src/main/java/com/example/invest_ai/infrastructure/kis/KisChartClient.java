package com.example.invest_ai.infrastructure.kis;

import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.infra.config.RedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * KIS REST API — 차트 데이터 조회 클라이언트
 *
 * 일봉: FHKST03010100, 분봉: FHKST03010200
 */
@Slf4j
@Component
public class KisChartClient {

    private final StringRedisTemplate redisTemplate;
    private final WebClient webClient;
    private final String appKey;
    private final String appSecret;

    public KisChartClient(
            StringRedisTemplate redisTemplate,
            @Value("${kis.api.rest-base-url}") String baseUrl,
            @Value("${kis.api.app-key}") String appKey,
            @Value("${kis.api.app-secret}") String appSecret
    ) {
        this.redisTemplate = redisTemplate;
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    /** KIS Access Token + 공통 헤더 빌드 */
    private WebClient.RequestHeadersSpec<?> withAuth(String uri, String trId) {
        String accessToken = redisTemplate.opsForValue().get(RedisKeys.kisAccessToken());
        if (accessToken == null || accessToken.isEmpty()) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "KIS Access Token이 없습니다.");
        }
        return webClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", trId)
                .accept(MediaType.APPLICATION_JSON);
    }

    public record CandleData(String date, String time, long open, long high, long low, long close, long volume) {}

    /**
     * 일봉/주봉/월봉/연봉 차트 데이터 조회
     */
    public List<CandleData> getDailyChart(String stockCode, String periodCode, String startDate, String endDate) {
        String uri = UriComponentsBuilder
                .fromPath("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", stockCode)
                .queryParam("FID_INPUT_DATE_1", startDate)
                .queryParam("FID_INPUT_DATE_2", endDate)
                .queryParam("FID_PERIOD_DIV_CODE", periodCode)
                .queryParam("FID_ORG_ADJ_PRC", "1")
                .toUriString();

        log.info("→ KIS 일봉 차트 요청: stockCode={}, period={}, {}~{}", stockCode, periodCode, startDate, endDate);

        try {
            Map<String, Object> response = withAuth(uri, "FHKST03010100")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                String rtCd = String.valueOf(response.get("rt_cd"));
                if (!"0".equals(rtCd)) {
                    String msg = response.get("msg1") != null ? String.valueOf(response.get("msg1")) : "알 수 없는 오류";
                    throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "KIS 차트 데이터 조회 실패: " + msg);
                }

                @SuppressWarnings("unchecked")
                List<Map<String, String>> output2 = (List<Map<String, String>>) response.get("output2");
                if (output2 == null) return List.of();

                List<CandleData> candles = new ArrayList<>();
                for (int i = output2.size() - 1; i >= 0; i--) {
                    Map<String, String> row = output2.get(i);
                    candles.add(new CandleData(
                            row.get("stck_bsop_date"), null,
                            parseLong(row.get("stck_oprc")),
                            parseLong(row.get("stck_hgpr")),
                            parseLong(row.get("stck_lwpr")),
                            parseLong(row.get("stck_clpr")),
                            parseLong(row.get("acml_vol"))
                    ));
                }
                log.info("← KIS 일봉 차트 수신: {}건", candles.size());
                return candles;
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("KIS 차트 API 통신 오류", e);
        }
        return List.of();
    }

    /**
     * 분봉 차트 데이터 조회 (30분 단위, 최대 30건)
     */
    public List<CandleData> getMinuteChart(String stockCode, String inquireHour) {
        String uri = UriComponentsBuilder
                .fromPath("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice")
                .queryParam("FID_ETC_CLS_CODE", "")
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", stockCode)
                .queryParam("FID_INPUT_HOUR_1", inquireHour)
                .queryParam("FID_PW_DATA_INCU_YN", "Y")
                .toUriString();

        try {
            Map<String, Object> response = withAuth(uri, "FHKST03010200")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                String rtCd = String.valueOf(response.get("rt_cd"));
                if (!"0".equals(rtCd)) return List.of();

                @SuppressWarnings("unchecked")
                List<Map<String, String>> output2 = (List<Map<String, String>>) response.get("output2");
                if (output2 == null) return List.of();

                List<CandleData> candles = new ArrayList<>();
                for (int i = output2.size() - 1; i >= 0; i--) {
                    Map<String, String> row = output2.get(i);
                    candles.add(new CandleData(
                            null, row.get("stck_cntg_hour"),
                            parseLong(row.get("stck_oprc")),
                            parseLong(row.get("stck_hgpr")),
                            parseLong(row.get("stck_lwpr")),
                            parseLong(row.get("stck_prpr")),
                            parseLong(row.get("cntg_vol"))
                    ));
                }
                return candles;
            }
        } catch (Exception e) {
            log.warn("KIS 분봉 조회 실패 (무시): stockCode={}, hour={}, err={}", stockCode, inquireHour, e.getMessage());
        }
        return List.of();
    }

    /**
     * 주식현재가 시세 조회 (FHKST01010100)
     * Redis miss 시 전일대비 등락률 fallback 용도
     */
    public record CurrentQuote(long price, double changeRate) {}

    public CurrentQuote getCurrentQuote(String stockCode) {
        String uri = UriComponentsBuilder
                .fromPath("/uapi/domestic-stock/v1/quotations/inquire-price")
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", stockCode)
                .toUriString();

        try {
            Map<String, Object> response = withAuth(uri, "FHKST01010100")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                String rtCd = String.valueOf(response.get("rt_cd"));
                if (!"0".equals(rtCd)) return null;

                @SuppressWarnings("unchecked")
                Map<String, String> output = (Map<String, String>) response.get("output");
                if (output != null) {
                    long price = parseLong(output.get("stck_prpr"));
                    double changeRate = parseDouble(output.get("prdy_ctrt"));
                    return new CurrentQuote(price, changeRate);
                }
            }
        } catch (Exception e) {
            log.warn("KIS 현재가 시세 조회 실패: stockCode={}, err={}", stockCode, e.getMessage());
        }
        return null;
    }

    private long parseLong(String value) {
        if (value == null || value.isEmpty()) return 0L;
        try { return Long.parseLong(value); } catch (NumberFormatException e) { return 0L; }
    }
    private double parseDouble(String value) {
        if (value == null || value.isEmpty()) return 0.0;
        try { return Double.parseDouble(value); } catch (NumberFormatException e) { return 0.0; }
    }
}