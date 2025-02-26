package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.domain.BackData;
import coin.cointrading.dto.OrderResponse;
import coin.cointrading.dto.UpbitCandle;
import coin.cointrading.exception.CustomException;
import coin.cointrading.exception.ErrorCode;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingService {

    private final ConcurrentHashMap<String, Future<?>> userTrades;
    private final ExecutorService executorService;
    private final Map<String, AtomicBoolean> userRunningStatus;
    private final BackDataRepository backDataRepository;
    private final UpbitService upbitService;
    private final UpbitCandleService upbitCandleService;

    // 프로그램 실행
    public void startTrading(AuthUser authUser) {
        String userId = authUser.getUserId();

        // 이미 실행 중인 거래 프로그램이 있는지 확인
        if (userTrades.containsKey(userId)) throw new CustomException(ErrorCode.TRADING_ALREADY_GENERATE);

        // 각 사용자의 running 상태 가져오기, 없으면 false로 초기화
        AtomicBoolean userRunning = userRunningStatus.computeIfAbsent(userId, k -> new AtomicBoolean(false));

        // 새로운 작업을 실행하고 Future로 저장
        Future<?> future = executorService.submit(() -> {
            try {
                // 거래 프로그램 실행 전에 running 상태를 true로 변경
                userRunning.set(true);
                startProgram(authUser);
            } catch (Exception e) {
                log.info("프로그램 실행 중 오류 발생: {}", e.getMessage());
            }
        });

        // 사용자별로 실행 상태를 userTrades에 저장
        userTrades.put(userId, future);
    }

    // 프로그램 종료
    public void stopTrading(AuthUser authUser) {
        String userId = authUser.getUserId();

        // 실행중인 프로그램 가져오기
        Future<?> future = userTrades.remove(userId);
        if (future == null) throw new CustomException(ErrorCode.TRADING_NOT_FOUND);

        // 각 사용자의 running 상태 가져오기
        AtomicBoolean userRunning = userRunningStatus.get(userId);

        // 사용자별로 running을 false로 설정하여 while문 종료
        userRunning.set(false);

        // 작업 종료
        try {
            future.cancel(true);
            log.info("{}의 거래 프로그램이 정상적으로 종료되었습니다.", userId);
        } catch (Exception e) {
            log.info("프로그램 종료 중 오류 발생: {}", e.getMessage());
        }
    }

    public void startProgram(AuthUser authUser) throws Exception {
        AtomicBoolean running = userRunningStatus.get(authUser.getUserId());
        log.info("프로그램 동작 시작 for user: {}", authUser.getUserId());

        double todayTarget = upbitCandleService.checkTarget(); // 당일 목표가
        boolean op_mode = false; // 시작하는 날 매수하지않기
        boolean hold = false; // 이미 매수한 상태라면 매수시도 하지않기(true = 매수, false = 매도)
        boolean stopLossExecuted = false; // 손절 여부 추적

        LocalTime alarmTime = LocalTime.now().plusMinutes(30);
        while (running.get()) {
            log.info("사용자: {}", authUser.getUserId());
            LocalTime now = LocalTime.now(); // 현재시간
            double current = upbitCandleService.current(); // 현재가
            double locked = 0;
            double sellLocked = 0;

            // 목표가 지정 9시 00분 20 ~ 30초 사이
            if (now.getHour() == 9 && now.getMinute() == 0 && (20 < now.getSecond() && 30 > now.getSecond())) {
                try {
                    todayTarget = upbitCandleService.checkTarget(); // 목표가 갱신
                    log.info("목표가 갱신: {}", todayTarget);
                    op_mode = true;
                    stopLossExecuted = false;
                } catch (Exception e) {
                    // 예외가 발생하면 로그에 기록
                    log.error("목표가 산정 중 오류 발생: {}", e.getMessage());
                }
            }

            // 매수 로직
            if (op_mode && !hold && current >= todayTarget && !stopLossExecuted) {
                // 매수 api
                OrderResponse orderResponse = (OrderResponse) upbitService.orderCoins("buy", authUser);
                hold = true;
                Thread.sleep(3000);

                locked = Math.round(Double.parseDouble(orderResponse.getLocked()));
                log.info("매수 금액: {}", locked);
            }

            // 매도 로직
            if (op_mode && hold && now.getHour() == 8 && now.getMinute() == 59 && (now.getSecond() >= 50 && now.getSecond() <= 59)) {
                try {
                    // 매도 api
                    upbitService.orderCoins("sell", authUser);
                    hold = false;
                    op_mode = false;

                    Thread.sleep(10000);

                    List<Map<String, Object>> orders = (List<Map<String, Object>>) upbitService.getOrders(authUser, 1);
                    Map<String, Object> order = orders.get(0);
                    Double executed_funds = Double.parseDouble((String) order.get("executed_funds"));
                    Double paid_fee = Double.parseDouble((String) order.get("paid_fee"));
                    sellLocked = Math.round(executed_funds - paid_fee);
                    double ror = Math.round((sellLocked - locked) / locked * 10.0) / 10.0;
                    log.info("매도 수익률: {}%", ror);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    // 예외가 발생하면 로그에 기록
                    log.error("매도 처리 중 오류 발생: {}", e.getMessage());
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

            try {
                Thread.sleep(1000); // 특정시간마다 하게
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    // 사용자별 실행 상태 확인
    public String checkStatus(AuthUser authUser) {
        String userId = authUser.getUserId();
        if (userTrades.containsKey(userId)) {
            return "true"; // 이미 실행 중
        } else {
            return "false"; // 실행 중 아님
        }
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
