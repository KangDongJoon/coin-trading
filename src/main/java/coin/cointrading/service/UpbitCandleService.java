package coin.cointrading.service;

import java.io.IOException;

public interface UpbitCandleService {

    String dayCandle() throws IOException;

    Double current() throws IOException;

    Double checkTarget() throws IOException;
}
