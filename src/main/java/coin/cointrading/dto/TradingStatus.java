package coin.cointrading.dto;

import coin.cointrading.domain.Coin;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicBoolean;

@Getter
public class TradingStatus {
    private final Coin selectCoin;
    private final AtomicBoolean opMode = new AtomicBoolean(false); // 거래 시작 여부
    private final AtomicBoolean hold = new AtomicBoolean(false); // 매수 여부

    public TradingStatus(Coin selectCoin) {
        this.selectCoin = selectCoin;
    }
}
