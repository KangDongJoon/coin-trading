package coin.cointrading.dto;

import lombok.Getter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Getter
public class TradingStatus {
    private AtomicBoolean opMode = new AtomicBoolean(true); // 거래 시작 여부
    private AtomicBoolean hold = new AtomicBoolean(false); // 매수 여부
    private AtomicBoolean stopLossExecuted = new AtomicBoolean(false); // 손절 여부

    private AtomicReference<Double> buyPrice = new AtomicReference<>(0.0);  // 매수 체결가
    private AtomicReference<Double> sellPrice = new AtomicReference<>(0.0); // 매도 체결가
}
