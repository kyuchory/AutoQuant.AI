package com.example.invest_ai.infrastructure.kis;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 국내 주식 정규장 시간(09:00~15:30, 평일) 판정 유틸.
 *
 * 장 마감 후~다음 장 시작 전에는 KIS로부터 실시간 체결 데이터가 오지 않는 것이 정상이므로,
 * 이 시간대에 "데이터 미수신 → 강제 재연결" 로직이 KIS 서버를 계속 두들기지 않도록
 * {@link KisWebsocketClient}의 헬스체크에서 이 판정을 사용한다.
 */
final class KisMarketHours {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime OPEN = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(15, 30);

    private KisMarketHours() {
    }

    static boolean isOpen() {
        ZonedDateTime now = ZonedDateTime.now(KST);
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(OPEN) && !time.isAfter(CLOSE);
    }
}
