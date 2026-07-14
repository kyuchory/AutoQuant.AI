package com.example.invest_ai.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "잘못된 입력 값입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다."),

    // Trade
    CONDITION_NOT_FOUND(HttpStatus.NOT_FOUND, "매매 조건을 찾을 수 없습니다."),

    // Asset
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "잔고가 부족합니다."),
    ASSET_NOT_FOUND(HttpStatus.NOT_FOUND, "자산 정보를 찾을 수 없습니다."),

    // Auth
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

    // Report
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "리포트를 찾을 수 없습니다."),
    REPORT_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "리포트 생성에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}