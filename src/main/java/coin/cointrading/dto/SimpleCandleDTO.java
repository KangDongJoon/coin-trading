package coin.cointrading.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SimpleCandleDTO {
    @JsonProperty("candle_date_time_kst")
    private final String candleDateTimeKst;

    @JsonProperty("opening_price")
    private final double openingPrice;

    @JsonProperty("high_price")
    private final double highPrice;

    @JsonProperty("low_price")
    private final double lowPrice;

    @JsonProperty("trade_price")
    private final double tradePrice;

    @JsonProperty("candle_acc_trade_volume")
    private final double candleAccTradeVolume;
}
