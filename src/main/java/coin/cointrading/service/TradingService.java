package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.dto.OrderResponse;
import coin.cointrading.dto.TradingStatus;
import coin.cointrading.exception.CustomException;
import coin.cointrading.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingService {

    private final TaskScheduler taskScheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> userScheduledTasks;
    private final ConcurrentHashMap<String, TradingStatus> userStatusMap;
    private final ConcurrentHashMap<String, AuthUser> userAuthMap;
    private final UpbitService upbitService;
    private final RedisService redisService;

    // 프로그램 실행
    public void startTrading(AuthUser authUser) {
        String userId = authUser.getUserId();

        // 실행중인 프로그램이 있는지 확인
        if (userScheduledTasks.containsKey(userId)) throw new CustomException(ErrorCode.TRADING_ALREADY_GENERATE);

        AtomicInteger i = new AtomicInteger();
        ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        initProgram(authUser);
                        startProgram(authUser);
                        if(i.incrementAndGet() == 3600){
                            TradingStatus status = userStatusMap.get(authUser.getUserId());
                            log.info("{}의 프로그램 동작 중, op_mode: {}, hold: {},", authUser.getUserId(), status.getOpMode().get(), status.getHold().get());
                            i.set(0);
                        }
                    } catch (Exception e) {
                        log.info("프로그램 실행 중 오류 발생: {}", e.getMessage());
                        throw new RuntimeException(e);
                    }
                },
                Duration.ofMillis(1000)
        );

        userScheduledTasks.put(authUser.getUserId(), future);
    }

    public void stopTrading(AuthUser authUser) {
        String userId = authUser.getUserId();

        ScheduledFuture<?> future = userScheduledTasks.get(userId);
        if (future == null) throw new CustomException(ErrorCode.TRADING_NOT_FOUND);

        try {
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                userScheduledTasks.remove(userId);
                log.info("{}의 거래 프로그램이 정상적으로 종료되었습니다.", userId);
            } else {
                log.warn("{}의 거래 프로그램 종료 요청이 실패했습니다.", userId);
            }
        } catch (Exception e) {
            log.error("{}의 프로그램 종료 중 오류 발생: {}", userId, e.getMessage(), e);
        }
    }

    public String checkStatus(AuthUser authUser) {
        if (userScheduledTasks.containsKey(authUser.getUserId())) return "true"; // 실행 중
        else return "false"; // 실행 중 아님
    }

    public void initProgram(AuthUser authUser) {
        userAuthMap.putIfAbsent(authUser.getUserId(), authUser);
        userStatusMap.putIfAbsent(authUser.getUserId(), new TradingStatus());
    }

    public void startProgram(AuthUser authUser) throws Exception {
        TradingStatus status = userStatusMap.get(authUser.getUserId());
        double todayTarget = 0;
        double current = redisService.getCurrentPrice();

        if(status.getOpMode().get()){
            todayTarget = redisService.getTargetPrice(); // 당일 목표가 캐싱
        }

        // 매수 로직
        if (current >= todayTarget && status.getOpMode().get() && !status.getStopLossExecuted().get() && !status.getHold().get()) {
            // 매수 api
            OrderResponse orderResponse = (OrderResponse) upbitService.orderCoins("buy", authUser);
            status.getHold().set(true);
            double locked = Math.round(Double.parseDouble(orderResponse.getLocked()));
            status.getBuyPrice().set(locked);
            log.info("{}의 매수 금액: {}원", authUser.getUserId(), locked);
        }

        // 손절 로직
        if (current <= status.getBuyPrice().get() * 0.95 && status.getHold().get()) {
            processSell(authUser, status);
            status.getStopLossExecuted().set(true);
        }
    }

    @Scheduled(cron = "50 59 8 * * ?")
    public void sellLogic() {
        // 각 사용자에 대해 별도의 쓰레드로 매도 처리
        for (String userId : userScheduledTasks.keySet()) {
            TradingStatus status = userStatusMap.get(userId);
            AuthUser authUser = userAuthMap.get(userId);

            boolean op_mode = status.getOpMode().get();
            boolean hold = status.getHold().get();
            boolean stopLossExecuted = status.getStopLossExecuted().get();

            if (op_mode && hold && !stopLossExecuted) processSell(authUser, status);
        }
    }

    // 매도 처리 로직
    private void processSell(AuthUser authUser, TradingStatus status) {
        try {
            upbitService.orderCoins("sell", authUser);

            // 상태 업데이트
            status.getOpMode().set(false);
            status.getHold().set(false);

            // 거래 결과 확인 (매도 이후)
            Thread.sleep(10000);

            List<Map<String, Object>> orders = (List<Map<String, Object>>) upbitService.getOrders(authUser, 1);
            Map<String, Object> order = orders.get(0);

            Double executedFunds = Double.parseDouble((String) order.get("executed_funds"));
            Double paidFee = Double.parseDouble((String) order.get("paid_fee"));
            double sellLocked = Math.round(executedFunds - paidFee);
            double locked = status.getBuyPrice().get();
            double ror = Math.round((sellLocked - locked) / locked * 10.0) / 10.0;

            log.info("{}의 매도 수익률: {}%", authUser.getUserId(), ror);

        } catch (Exception e) {
            log.error("{}의 매도 처리 중 오류 발생: {}", authUser.getUserId(), e.getMessage());
        }
    }
}
