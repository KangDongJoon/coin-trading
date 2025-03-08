package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.dto.OrderResponse;
import coin.cointrading.dto.TradingStatus;
import coin.cointrading.exception.CustomException;
import coin.cointrading.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingService {

    private final ConcurrentHashMap<String, TradingStatus> userStatusMap;
    private final ConcurrentHashMap<String, AuthUser> userAuthMap;
    private final Set<String> runningUser;
    private final UpbitService upbitService;
    private final RedisService redisService;

    // 프로그램 실행
    public void startTrading(AuthUser authUser) {
        initProgram(authUser);
        runningUser.add(authUser.getUserId());
        log.info("{}의 프로그램이 실행되었습니다.", authUser.getUserId());
    }

    // 프로그램 종료
    public void stopTrading(AuthUser authUser) {
        runningUser.remove(authUser.getUserId());
        log.info("{}의 프로그램이 종료되었습니다.", authUser.getUserId());
    }

    public String checkStatus(AuthUser authUser) {
        if (runningUser.contains(authUser.getUserId())) return "true"; // 실행 중
        else return "false"; // 실행 중 아님
    }

    private void initProgram(AuthUser authUser) {
        userAuthMap.putIfAbsent(authUser.getUserId(), authUser);
        userStatusMap.putIfAbsent(authUser.getUserId(), new TradingStatus());
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void programStatus() {
        for (String userId : runningUser) {
            TradingStatus status = userStatusMap.get(userId);
            log.info("---{}의 프로그램 동작중--- op_mode: {}, hold: {}", userId, status.getOpMode().get(), status.getHold().get());
        }
    }

    @Scheduled(fixedRate = 1000)
    public void checkPrice() {
        double currentPrice = redisService.getCurrentPrice();
        double targetPrice = redisService.getTargetPrice();

        if (currentPrice >= targetPrice) {
            processBuy();
        }

        if (currentPrice <= targetPrice * 0.95) {
            processExecute();
        }
    }

    private void processBuy() {
        for (String userId : runningUser) {
            TradingStatus status = userStatusMap.get(userId);
            if (status.getOpMode().get() && !status.getStopLossExecuted().get() && !status.getHold().get()) {
                AuthUser authUser = userAuthMap.get(userId);
                executeAsyncBuy(authUser, status);
            }
        }
    }

    private void executeAsyncBuy(AuthUser authUser, TradingStatus status) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return upbitService.orderCoins("buy", authUser);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new CustomException(ErrorCode.UPBIT_ORDER_FAIL);
            }
        }).thenAccept(result -> afterBuy(result, status, authUser));
    }

    @Scheduled(cron = "50 59 8 * * ?")
    public void processSell() {
        for (String userId : runningUser) {
            TradingStatus status = userStatusMap.get(userId);
            if (status.getOpMode().get() && !status.getStopLossExecuted().get() && status.getHold().get()) {
                AuthUser authUser = userAuthMap.get(userId);
                executeAsyncSell(authUser, status);
            }
        }
    }

    private void processExecute() {
        for (String userId : runningUser) {
            TradingStatus status = userStatusMap.get(userId);
            if (status.getOpMode().get() && !status.getStopLossExecuted().get() && status.getHold().get()) {
                AuthUser authUser = userAuthMap.get(userId);
                executeAsyncSell(authUser, status);
                status.getStopLossExecuted().set(true);
            }
        }
    }

    private void executeAsyncSell(AuthUser authUser, TradingStatus status) {
        CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return upbitService.orderCoins("sell", authUser);
                    } catch (Exception e) {
                        log.error(e.getMessage());
                        throw new CustomException(ErrorCode.UPBIT_ORDER_FAIL);
                    }
                }
        ).thenCompose(orderResponse ->
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return upbitService.getOrders(authUser, 1);
                            } catch (Exception e) {
                                log.error(e.getMessage());
                                throw new CustomException(ErrorCode.UPBIT_ORDER_LIST_READ_FAIL);
                            }
                        }
                ).thenAccept(result -> {
                    afterSell(result, status, authUser);
                })
        );
    }


    private void afterBuy(Object result, TradingStatus status, AuthUser authUser) {
        OrderResponse response = (OrderResponse) result;
        status.getHold().set(true);  // 매수 완료 상태로 변경
        double locked = Math.round(Double.parseDouble(response.getLocked()));
        status.getBuyPrice().set(locked);  // 매수 금액 설정
        log.info("{}의 매수 금액: {}원", authUser.getUserId(), locked);
    }

    private void afterSell(Object result, TradingStatus status, AuthUser authUser) {
        status.getHold().set(false);
        List<Map<String, Object>> orders = (List<Map<String, Object>>) result;
        Map<String, Object> order = orders.get(0);
        Double executedFunds = Double.parseDouble((String) order.get("executed_funds"));
        Double paidFee = Double.parseDouble((String) order.get("paid_fee"));
        double sellLocked = Math.round(executedFunds - paidFee);
        double locked = status.getBuyPrice().get();
        double ror = Math.round((sellLocked - locked) / locked * 10.0) / 10.0;
        log.info("{}의 매도 수익률: {}%", authUser.getUserId(), ror);
    }
}
