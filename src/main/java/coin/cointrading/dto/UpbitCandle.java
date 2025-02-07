package coin.cointrading.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpbitCandle {
    private String market;

    @JsonProperty("candle_date_time_utc")
    private String candleDateTimeUtc;

    @JsonProperty("candle_date_time_kst")
    private String candleDateTimeKst;

    @JsonProperty("opening_price")
    private double openingPrice;

    @JsonProperty("high_price")
    private double highPrice;

    @JsonProperty("low_price")
    private double lowPrice;

    @JsonProperty("trade_price")
    private double tradePrice;

    @JsonProperty("prev_closing_price")
    private double prevClosingPrice;

    @JsonProperty("change_price")
    private double changePrice;

    @JsonProperty("change_rate")
    private double changeRate;

    @JsonProperty("converted_trade_price")
    private double convertedTradePrice;

    private long timestamp;

    @JsonProperty("candle_acc_trade_price")
    private double candleAccTradePrice;

    @JsonProperty("candle_acc_trade_volume")
    private double candleAccTradeVolume;

    @JsonProperty("first_day_of_period")
    private String firstDayOfPeriod;
}