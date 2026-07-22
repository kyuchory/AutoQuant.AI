package com.example.invest_ai.global.error;

import com.example.invest_ai.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        log.warn("CustomException: {}", e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(
                        e.getErrorCode().name(),
                        e.getMessage()
                ));
    }

    /** MySQL 제약조건 위반 → CustomException 매핑 (clinerules.md §2.6) */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String causeMsg = e.getMostSpecificCause().getMessage();
        log.warn("DataIntegrityViolationException: {}", causeMsg);

        // uk_holdings_user_stock → 중복 보유
        if (causeMsg != null && causeMsg.contains("uk_holdings_user_stock")) {
            return ResponseEntity.status(409).body(
                    ApiResponse.error(ErrorCode.DUPLICATE_HOLDING.name(), ErrorCode.DUPLICATE_HOLDING.getMessage()));
        }
        // uk_news_url → 내부 Skip (예외로 노출하지 않음)
        if (causeMsg != null && causeMsg.contains("uk_news_url")) {
            log.info("중복 뉴스 URL Skip: {}", causeMsg);
            return ResponseEntity.ok(ApiResponse.success(null));
        }

        log.error("Unhandled DataIntegrityViolationException: ", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.error(
                        ErrorCode.INTERNAL_SERVER_ERROR.name(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled Exception: ", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.error(
                        ErrorCode.INTERNAL_SERVER_ERROR.name(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                ));
    }
}
