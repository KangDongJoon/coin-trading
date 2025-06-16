package coin.cointrading.service;

import java.io.IOException;

public interface UpbitCandleService {

    String dayCandle(String coin) throws IOException;

    Double current(String coin) throws IOException;

    Double checkTarget(String coin) throws IOException;
}
