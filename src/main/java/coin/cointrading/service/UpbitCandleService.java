package coin.cointrading.service;

import coin.cointrading.domain.Coin;

import java.io.IOException;

public interface UpbitCandleService {

    String dayCandle(Coin coin) throws IOException;

    Double current(Coin coin) throws IOException;

    Double checkTarget(Coin coin) throws IOException;
}
