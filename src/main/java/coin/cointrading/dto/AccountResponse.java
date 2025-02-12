package coin.cointrading.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountResponse {

    private String currency;
    private String balance;
    private String locked;
    @JsonProperty("avg_buy_price")
    private String avgBuyPrice;
    @JsonProperty("avg_buy_price_modified")
    private Boolean avgBuyPriceModified;
    @JsonProperty("unit_currency")
    private String unitCurrency;
}
