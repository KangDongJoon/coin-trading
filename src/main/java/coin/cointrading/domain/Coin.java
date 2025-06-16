package coin.cointrading.domain;

import lombok.Getter;

@Getter
public enum Coin {
    BTC("비트코인", "KRW-BTC"),
    ETH("이더리움", "KRW-ETH"),
    XRP("리플", "KRW-XRP");

    private final String koreanName;
    private final String marketCode;

    Coin(String koreanName, String marketCode) {
        this.koreanName = koreanName;
        this.marketCode = marketCode;
    }
}
