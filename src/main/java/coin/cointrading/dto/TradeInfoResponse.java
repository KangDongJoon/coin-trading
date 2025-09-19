package coin.cointrading.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TradeInfoResponse {

    private String tradingDay;
    private String tradeCoin;
    private String returnRate;
    private int beforeMoney;
    private int afterMoney;

}
