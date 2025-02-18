package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.domain.BackData;
import coin.cointrading.dto.OrderResponse;
import coin.cointrading.dto.UpbitCandle;
import coin.cointrading.repository.BackDataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingService {

    private final BackDataRepository backDataRepository;
    private final UpbitService upbitService;
    private boolean running = false;

    public void startProgram(AuthUser authUser) throws Exception {
        log.info("프로그램 동작 시작");
        running = true;
        double todayTarget = upbitService.checkTarget(); // 당일 목표가
        boolean op_mode = false; // 시작하는 날 매수하지않기
        boolean hold = false; // 이미 매수한 상태라면 매수시도 하지않기(true = 매수, false = 매도)
        boolean stopLossExecuted = false; // 손절 여부 추적

        LocalTime alarmTime = LocalTime.now().plusMinutes(30);
        while (running) {
            LocalTime now = LocalTime.now(); // 현재시간
            double current = upbitService.current(); // 현재가
            // 목표가 지정 9시 00분 20 ~ 30초 사이
            if (now.getHour() == 9 && now.getMinute() == 0 && (20 < now.getSecond() && 30 > now.getSecond())) {
                try {
                    todayTarget = upbitService.checkTarget(); // 목표가 갱신
                    log.info("목표가 갱신: {}", todayTarget);
                    op_mode = true;
                    stopLossExecuted = false;
                } catch (Exception e) {
                    // 예외가 발생하면 로그에 기록
                    log.error("목표가 산정 중 오류 발생: {}", e.getMessage());
                    e.printStackTrace();
                }
            }

            // 매수 로직
            if (op_mode && !hold && current >= todayTarget && !stopLossExecuted) {
                // 매수 api
                OrderResponse orderResponse = upbitService.orderCoins("buy", authUser);
                hold = true;
                log.info("매수 수량: {}", orderResponse.getVolume());
            }

            // 매도 로직
            if (op_mode && hold && now.getHour() == 8 && now.getMinute() == 59 && (now.getSecond() >= 50 && now.getSecond() <= 59)) {
                try {
                    // 매도 api
                    OrderResponse orderResponse = upbitService.orderCoins("sell", authUser);
                    hold = false;
                    op_mode = false;
                    double ror = ((Integer.parseInt(orderResponse.getPrice()) - todayTarget) / todayTarget) * 100;
                    ror = Math.round(ror * 10.0) / 10.0; // 소수점 한 자리까지 반올림
                    log.info("매도 수익률: {}%", ror);
                    Thread.sleep(10000);
                } catch (Exception e) {
                    // 예외가 발생하면 로그에 기록
                    log.error("매도 처리 중 오류 발생: {}", e.getMessage());
                    e.printStackTrace();
                }
            }

            // 손절 5%
            if (op_mode && hold && current < todayTarget * 0.95) {
                // 매도 api
                upbitService.orderCoins("sell", authUser);
                log.info("손절 발생");
                hold = false;
                op_mode = false;
                stopLossExecuted = true;
            }

            if (now.getMinute() == alarmTime.getMinute()) {
                log.info("---프로그램 실행 중--- 목표가: {}   매수여부: {}  ", todayTarget, hold);
                alarmTime = now.plusMinutes(30);
            }

            Thread.sleep(1000); // 특정시간마다 하게
        }
    }

    public void stopProgram() {
        running = false;
        log.info("프로그램 종료");
    }

    public boolean statusProgram() {
        return running;
    }

    @Transactional
    public void postBackData() throws IOException {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://api.upbit.com/v1/candles/days?market=KRW-ETH&count=3")
                .get()
                .addHeader("accept", "application/json")
                .build();

        // try-with-resources 구문을 사용하여 자동으로 닫히도록 함
        try (Response response = client.newCall(request).execute()) {
            String jsonResponse = response.body().string();

            // Jackson을 이용한 JSON 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            UpbitCandle[] candles = objectMapper.readValue(jsonResponse, UpbitCandle[].class);

            for (int i = 1; i < candles.length - 1; i++) {
                String day = candles[i].getCandleDateTimeKst().substring(0, 10); // 2025-02-16
                double targetPrice = candles[i + 1].getTradePrice() + (candles[i + 1].getHighPrice() - candles[i + 1].getLowPrice()) * 0.5;
                double todayHighPrice = candles[i].getHighPrice();
                String tradingStatus = todayHighPrice >= targetPrice ? "O" : "X";
                double returnRate = Math.round((((candles[i].getTradePrice() - targetPrice) / targetPrice) * 100) * 10.0) / 10.0;

                BackData backData = new BackData(
                        day,
                        tradingStatus,
                        returnRate);

                backDataRepository.save(backData);
            }
        }
    }

    public List<BackData> getBackData() {
        return backDataRepository.findAllActiveTrading();
    }

    @Transactional
    public void initBackData() throws IOException {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://api.upbit.com/v1/candles/days?market=KRW-ETH&count=200")
                .get()
                .addHeader("accept", "application/json")
                .build();

        // try-with-resources 구문을 사용하여 자동으로 닫히도록 함
        try (Response response = client.newCall(request).execute()) {
            String jsonResponse = response.body().string();

            // Jackson을 이용한 JSON 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            UpbitCandle[] candles = objectMapper.readValue(jsonResponse, UpbitCandle[].class);

            for (int i = 1; i < candles.length - 1; i++) {
                String day = candles[i].getCandleDateTimeKst().substring(0, 10); // 2025-02-16
                double targetPrice = candles[i + 1].getTradePrice() + (candles[i + 1].getHighPrice() - candles[i + 1].getLowPrice()) * 0.5;
                double todayHighPrice = candles[i].getHighPrice();
                String tradingStatus = todayHighPrice >= targetPrice ? "O" : "X";
                double returnRate = Math.round((((candles[i].getTradePrice() - targetPrice) / targetPrice) * 100) * 10.0) / 10.0;

                BackData backData = new BackData(
                        day,
                        tradingStatus,
                        returnRate);

                backDataRepository.save(backData);
            }
        }
    }
}
