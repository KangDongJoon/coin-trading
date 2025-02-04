package coin.cointrading.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountResponse {

    @Schema(description = "종목", example = "KRW")
    private String currency;
    @Schema(description = "수량", example = "90000")
    private String balance;
    @Schema(description = "주문 중 묶여있는 금액/수량", example = "0")
    private String locked;
    @JsonProperty("avg_buy_price")
    @Schema(description = "매수평균가", example = "0")
    private String avgBuyPrice;
    @JsonProperty("avg_buy_price_modified")
    @Schema(description = "매수평균가 수정 여부", example = "False")
    private Boolean avgBuyPriceModified;
    @JsonProperty("unit_currency")
    @Schema(description = "화폐단위", example = "KRW")
    private String unitCurrency;
}
