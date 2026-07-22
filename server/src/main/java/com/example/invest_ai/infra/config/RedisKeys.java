package com.example.invest_ai.infra.config;

/**
 * Redis 키 네이밍 컨벤션: {domain}:{sub-domain?}:{identifier}:{data-type}
 *
 * .redisflow.txt v1 문서의 9개 Key 패턴을 Java 상수와 메서드로 정의합니다.
 * 모든 Redis 키는 이 클래스를 통해서만 생성하며, 임의로 패턴을 변경하지 않습니다.
 */
public final class RedisKeys {

    private RedisKeys() {
        // 인스턴스 생성 금지 (유틸리티 클래스)
    }

    // ========================================================================
    // Domain 상수
    // ========================================================================
    private static final String DOMAIN_PRICE = "price";
    private static final String DOMAIN_CHART  = "chart";
    private static final String DOMAIN_REPORT = "report";
    private static final String DOMAIN_AUTH = "auth";
    private static final String DOMAIN_KIS = "kis";
    private static final String DOMAIN_RATE = "rate";
    private static final String DOMAIN_WS = "ws";

    // ========================================================================
    // 1. 시세: price:{stockCode}:current
    // ========================================================================
    private static final String SUB_PRICE = null; // sub-domain 없음
    private static final String TYPE_PRICE_CURRENT = "current";

    /**
     * 실시간 현재 체결가 키를 반환합니다.
     * Writer: KisWebsocketClient
     * Reader: AssetSummaryService, 조건 매칭 워커
     * TTL: 없음 (KIS Websocket이 체결가 수신 시마다 SET으로 덮어씀)
     */
    public static String priceCurrent(String stockCode) {
        return key(DOMAIN_PRICE, stockCode, TYPE_PRICE_CURRENT);
    }

    // ========================================================================
    // 2. AI 리포트: report:{stockCode}:text
    // ========================================================================
    private static final String SUB_REPORT = null; // sub-domain 없음
    private static final String TYPE_REPORT_TEXT = "text";

    /**
     * AI 투자 리포트 캐시 키를 반환합니다.
     * Writer: RabbitMQ ReportWorker
     * Reader: GET /reports/stocks/{stockCode}
     * TTL: 43200초 (12시간)
     * Cache Invalidation: 신규 뉴스 INSERT 시 즉시 DEL
     */
    public static String reportText(String stockCode) {
        return key(DOMAIN_REPORT, stockCode, TYPE_REPORT_TEXT);
    }

    // ========================================================================
    // 3. 인증 - Refresh Token: auth:{userId}:refreshToken
    // ========================================================================
    private static final String SUB_AUTH_REFRESH = null; // sub-domain 없음
    private static final String TYPE_AUTH_REFRESH = "refreshToken";

    /**
     * Refresh Token 저장 키를 반환합니다.
     * Writer: AuthService (로그인/refresh 시)
     * Reader: POST /auth/refresh
     * TTL: 1209600초 (14일)
     */
    public static String authRefreshToken(Long userId) {
        return key(DOMAIN_AUTH, String.valueOf(userId), TYPE_AUTH_REFRESH);
    }

    // ========================================================================
    // 4. 인증 - Access Token 블랙리스트: auth:{jti}:blacklist
    // ========================================================================
    private static final String TYPE_AUTH_BLACKLIST = "blacklist";

    /**
     * 로그아웃된 Access Token 블랙리스트 키를 반환합니다.
     * Writer: AuthService (로그아웃 시)
     * Reader: JWT 인증 필터 (매 요청)
     * TTL: 해당 Access Token의 남은 유효시간(초)
     * Value: "1" (존재 여부만 확인)
     */
    public static String authBlacklist(String jti) {
        return key(DOMAIN_AUTH, jti, TYPE_AUTH_BLACKLIST);
    }

    // ========================================================================
    // 5. KIS 외부인증 - Access Token: kis:auth:accessToken
    // ========================================================================
    private static final String SUB_KIS_AUTH = "auth";
    private static final String TYPE_KIS_ACCESS_TOKEN = "accessToken";

    /**
     * KIS REST API Access Token 키를 반환합니다.
     * Writer: KisAuthClient (앱 기동 시 + 5.5시간 주기 스케줄러)
     * Reader: KisWebsocketClient, 주문 실행 클라이언트
     * TTL: 21000초 (5시간 50분) — KIS 공식 24시간보다 짧게 설정, 6시간 주기 선제 갱신 대비
     * 비고: 애플리케이션 전역 단일 키 (userId 없음)
     */
    public static String kisAccessToken() {
        return key(DOMAIN_KIS, SUB_KIS_AUTH, TYPE_KIS_ACCESS_TOKEN);
    }

    // ========================================================================
    // 6. KIS 외부인증 - 웹소켓 접속키: kis:auth:approvalKey
    // ========================================================================
    private static final String TYPE_KIS_APPROVAL_KEY = "approvalKey";

    /**
     * KIS WebSocket 접속키(approval_key) 키를 반환합니다.
     * Writer: KisAuthClient (앱 기동 시 + 재연결 시)
     * Reader: KisWebsocketClient
     * TTL: 21000초 (5시간 50분)
     * 비고: 애플리케이션 전역 단일 키 (userId 없음)
     */
    public static String kisApprovalKey() {
        return key(DOMAIN_KIS, SUB_KIS_AUTH, TYPE_KIS_APPROVAL_KEY);
    }

    // ========================================================================
    // 7. 동시성 락: rate:order:lock:{userId}:{stockCode}
    // ========================================================================
    private static final String SUB_RATE_ORDER = "order";
    private static final String TYPE_RATE_LOCK = "lock";

    /**
     * 자동매매 동시성 락 키를 반환합니다.
     * Writer: 조건 매칭 워커 (주문 시도 직전)
     * Reader: 동일 워커 (락 존재 여부 확인)
     * TTL: 4초 (SET ... NX EX 4)
     * Value: "locked"
     */
    public static String rateOrderLock(Long userId, String stockCode) {
        return keyHierarchical(DOMAIN_RATE, SUB_RATE_ORDER + ":lock", String.valueOf(userId), stockCode);
    }

    // ========================================================================
    // 8. KIS 전역 레이트리미터: rate:kis:global:orderCount:{epochSecond}
    // ========================================================================
    private static final String SUB_RATE_KIS = "kis";
    private static final String TYPE_RATE_ORDER_COUNT = "orderCount";

    /**
     * KIS REST API 초당 호출 횟수 카운터 키를 반환합니다.
     * Writer: 주문 실행 클라이언트 (호출 직전 INCR)
     * Reader: 동일 클라이언트 (한도 초과 시 큐잉)
     * TTL: 2초 (초 단위 키 자체 회전)
     */
    public static String rateKisOrderCount(long epochSecond) {
        return keyHierarchical(DOMAIN_RATE, SUB_RATE_KIS + ":global", "orderCount", String.valueOf(epochSecond));
    }

    // ========================================================================
    // 9. 웹소켓 라우팅: ws:session:{userId} (보류 — 확장 포인트)
    // ========================================================================
    private static final String SUB_WS_SESSION = "session";

    /**
     * 사용자 WebSocket 세션 라우팅 키를 반환합니다.
     * 현재는 미구현 (단일 서버에서는 로컬 메모리 세션 맵으로 충분).
     * 스케일아웃 시 Redis Pub/Sub 브로드캐스트 방식 도입 검토.
     */
    public static String wsSession(Long userId) {
        return key(DOMAIN_WS, SUB_WS_SESSION, String.valueOf(userId));
    }

    // ========================================================================
    // Key 빌더
    // ========================================================================

    /**
     * {domain}:{identifier}:{data-type} 형태의 키를 생성합니다.
     */
    private static String key(String domain, String identifier, String dataType) {
        return domain + ":" + identifier + ":" + dataType;
    }

    /**
     * {domain}:{sub-domain}:{data-type} 형태의 키를 생성합니다.
     * (identifier 없이 sub-domain만 있는 경우)
     */
    private static String key(String domain, String subDomain, String dataType, boolean useSubDomain) {
        return domain + ":" + subDomain + ":" + dataType;
    }

    /**
     * {domain}:{sub-domain-part1}:{sub-domain-part2}:{data-type} 형태의 키를 생성합니다.
     * rate:order:lock:{userId}:{stockCode} 등 계층이 3단계 이상인 경우 사용
     */
    private static String keyHierarchical(String domain, String subDomain, String identifier, String dataType) {
        return domain + ":" + subDomain + ":" + identifier + ":" + dataType;
    }
}