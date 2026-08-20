package com.example.invest_ai.domain;

import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.domain.trade.dto.ConditionDto.TradingConditionRequest;
import com.example.invest_ai.domain.trade.dto.ConditionDto.TradingConditionResponse;
import com.example.invest_ai.domain.trade.dto.ConditionDto.TriggerRequest;
import com.example.invest_ai.domain.trade.entity.TradingCondition;
import com.example.invest_ai.domain.trade.repository.TradingConditionRepository;
import com.example.invest_ai.domain.trade.service.ConditionService;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.infrastructure.redis.RedisPriceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * docs/incident-trading-engine-blocking.md에 기록된 회귀 방지 테스트.
 *
 * 트레일링 스탑의 targetValue는 "고점 대비 -N%"를 뜻하는 음수여야 한다. 0 이상이 들어가면
 * 임계값이 고점과 같거나 높아져 BELOW 비교가 매 틱마다 참이 되어버리는 문제가 있었다.
 */
@ExtendWith(MockitoExtension.class)
class ConditionServiceTest {

    private static final Long USER_ID = 1L;
    private static final String STOCK_CODE = "005930";

    @Mock private TradingConditionRepository conditionRepository;
    @Mock private StockRepository stockRepository;
    @Mock private RedisPriceClient redisPriceClient;
    @InjectMocks private ConditionService conditionService;

    private Stock stock() {
        return Stock.builder().stockCode(STOCK_CODE).stockName("삼성전자").build();
    }

    private TradingConditionRequest requestWithTrailingStop(BigDecimal targetValue) {
        TriggerRequest trigger = new TriggerRequest("TRAILING_STOP", "HIGHEST_PRICE", "BELOW", targetValue, true);
        return new TradingConditionRequest(
                STOCK_CODE, "SELL", 1, "MARKET", null, "AND", "AUTO", true, List.of(trigger));
    }

    @Test
    @DisplayName("createCondition: 트레일링 스탑 targetValue가 0 이상이면 즉시 거부한다")
    void 트레일링스탑_targetValue_0이상이면_예외() {
        TradingConditionRequest request = requestWithTrailingStop(new BigDecimal("5"));

        assertThatThrownBy(() -> conditionService.createCondition(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_TRIGGER));
    }

    @Test
    @DisplayName("createCondition: 트레일링 스탑 targetValue가 0이면 즉시 거부한다 (경계값)")
    void 트레일링스탑_targetValue_0이면_예외() {
        TradingConditionRequest request = requestWithTrailingStop(BigDecimal.ZERO);

        assertThatThrownBy(() -> conditionService.createCondition(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_TRIGGER));
    }

    @Test
    @DisplayName("createCondition: 트레일링 스탑 targetValue가 음수면 정상 등록된다")
    void 트레일링스탑_targetValue_음수면_정상등록() {
        TradingConditionRequest request = requestWithTrailingStop(new BigDecimal("-5"));

        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock()));
        given(redisPriceClient.getCurrentPrice(STOCK_CODE)).willReturn(new BigDecimal("80000"));
        given(conditionRepository.save(any(TradingCondition.class))).willAnswer(inv -> inv.getArgument(0));

        TradingConditionResponse response = conditionService.createCondition(USER_ID, request);

        assertThat(response.triggers()).hasSize(1);
        assertThat(response.triggers().get(0).trailingHighest()).isEqualByComparingTo("80000");
    }
}
