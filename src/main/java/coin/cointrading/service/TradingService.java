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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingService {

    private final ConcurrentHashMap<String, TradingStatus> userStatusMap; // 유저 거래상태 저장 컬렉션
    private final ConcurrentHashMap<String, AuthUser> userAuthMap; // 유저 Auth정보 저장 컬렉션
    private final Set<String> runningUser; // 현재 프로그램을 실행중인 유저를 저장하는 컬렉션
    private final SchedulerControlService schedulerControlService;
    private final UpbitService upbitService;
    private final RedisService redisService;
    private final ExecutorService executor;

    /**
     * 프로그램 실행
     *
     * @param authUser 로그인 유저
     */
    public void startTrading(AuthUser authUser) {
        initProgram(authUser);
        runningUser.add(authUser.getUserId());
        log.info("{}의 프로그램이 실행되었습니다.", authUser.getUserId());
        log.info("금일 목표가: {}원", redisService.getTargetPrice());
    }

    /**
     * 프로그램 종료, 서버에서 상태 및 Auth정보도 함께 삭제
     *
     * @param authUser 로그인 유저
     */
    public void stopTrading(AuthUser authUser) {
        runningUser.remove(authUser.getUserId());
        userAuthMap.remove(authUser.getUserId());
        userStatusMap.remove(authUser.getUserId());
        log.info("{}의 프로그램이 종료되었습니다.", authUser.getUserId());
    }

    /**
     * 프론트 화면 버튼 전환을 위한 프로그램 실행상태 확인
     *
     * @param authUser 로그인 유저
     * @return 상태
     */
    public String checkStatus(AuthUser authUser) {
        if (runningUser.contains(authUser.getUserId())) return "true"; // 실행 중
        else return "false"; // 실행 중 아님
    }

    /**
     * 최초 실행 시 상태 및 Auth정보 서버에 추가
     *
     * @param authUser 로그인 유저
     */
    private void initProgram(AuthUser authUser) {
        userAuthMap.putIfAbsent(authUser.getUserId(), authUser);
        userStatusMap.putIfAbsent(authUser.getUserId(), new TradingStatus());
    }

    /**
     * 1시간마다 유저 별 프로그램 동작상태 확인
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void programStatus() {
        for (String userId : runningUser) {
            TradingStatus status = userStatusMap.get(userId);
            log.info("---{}의 프로그램 동작중--- op_mode: {}, hold: {}", userId, status.getOpMode().get(), status.getHold().get());
        }
    }

    /**
     * 1초마다 코인 시세 확인 후 매수, 손절 진행
     */
    @Scheduled(fixedDelay = 1000)
    public void checkPrice() {
        if (schedulerControlService.getIsProcessing()) {
            return;
        }

        schedulerControlService.setIsProcessing(true); // 🔹 실행 시작 표시

        try {
            double currentPrice = redisService.getCurrentPrice();
            double targetPrice = redisService.getTargetPrice();
            String todayTradeCheck = redisService.getTodayTradeCheck();

            if (todayTradeCheck.equals("false") && currentPrice >= targetPrice) {
                processBuy()
                        .thenRun(() -> schedulerControlService.setIsProcessing(false));  // 🔹 비동기 완료 후 해제
            }

            if (todayTradeCheck.equals("true") && currentPrice <= targetPrice * 0.95) {
                processExecute();
            } else {
                schedulerControlService.setIsProcessing(false);
            }

        } catch (Exception e) {
            log.error("🚨 checkPrice() 실행 중 오류 발생: {}", e.getMessage());
            schedulerControlService.setIsProcessing(false);
        }
    }


    /**
     * 조건에 부합하면 매수 진행
     */
    private CompletableFuture<Void> processBuy() {
        log.info("----------매수 로직 실행 중---------- ");

        List<CompletableFuture<Void>> futures = runningUser.stream()
                .map(userId -> {
                    TradingStatus status = userStatusMap.get(userId);
                    if (status.getOpMode().get() && !status.getStopLossExecuted().get() && !status.getHold().get()) {
                        AuthUser authUser = userAuthMap.get(userId);
                        return executeAsyncBuy(authUser, status);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();

        redisService.setTodayTradeCheck("true");
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * 비동기 처리로 Upbit 매수 API 요청 및 상태 변경
     *
     * @param authUser 로그인 유저
     * @param status   유저 거래 상태
     */
    private CompletableFuture<Void> executeAsyncBuy(AuthUser authUser, TradingStatus status) {
        return CompletableFuture.supplyAsync(() -> {  // 🔹 'return' 추가
            try {
                return upbitService.orderCoins("buy", authUser);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new CustomException(ErrorCode.UPBIT_ORDER_FAIL);
            }
        }, executor).thenAccept(result -> afterBuy(result, status, authUser));
    }

    /**
     * 매일 장 종료 시 전량 매도 처리
     */
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

    /**
     * 조건에 부합하면 손전 진행
     */
    private void processExecute() {
        log.info("----------손절 로직 실행 중---------- ");

        List<CompletableFuture<Void>> futures = runningUser.stream()
                .map(userId -> {
                    TradingStatus status = userStatusMap.get(userId);
                    if (status.getOpMode().get() && !status.getStopLossExecuted().get() && status.getHold().get()) {
                        AuthUser authUser = userAuthMap.get(userId);
                        status.getStopLossExecuted().set(true);
                        return executeAsyncSell(authUser, status);
                    }
                    return null;
                })
                .filter(Objects::nonNull) // ✅ null을 제거하여 올바른 CompletableFuture 리스트 생성
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }


    /**
     * 비동기 처리로 Upbit 매도 API 요청 및 상태 변경
     *
     * @param authUser 로그인 유저
     * @param status   거래 상태
     */
    private CompletableFuture<Void> executeAsyncSell(AuthUser authUser, TradingStatus status) {
        log.info("----------매도 로직 실행 중---------- ");
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return upbitService.orderCoins("sell", authUser);
                    } catch (Exception e) {
                        log.error(e.getMessage());
                        throw new CustomException(ErrorCode.UPBIT_ORDER_FAIL);
                    }
                }, executor
        ).thenCompose(orderResponse ->
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return upbitService.getOrders(authUser, 1);
                            } catch (Exception e) {
                                log.error(e.getMessage());
                                throw new CustomException(ErrorCode.UPBIT_ORDER_LIST_READ_FAIL);
                            }
                        }, executor
                ).thenAccept(result -> afterSell(result, status, authUser))
        );
    }

    /**
     * 매수 처리 이후 상태 변경 및 매수금액 확인
     *
     * @param result   거래 결과
     * @param status   거래 상태
     * @param authUser 로그인 유저
     */
    private void afterBuy(Object result, TradingStatus status, AuthUser authUser) {
        OrderResponse response = (OrderResponse) result;
        status.getHold().set(true);  // 매수 완료 상태로 변경
        double locked = Math.round(Double.parseDouble(response.getLocked()));
        status.getBuyPrice().set(locked);  // 매수 금액 설정
        log.info("{}의 매수 금액: {}원", authUser.getUserId(), locked);
    }

    /**
     * 매도 처리 후 상태 변경 및 수익률 확인
     *
     * @param result   거래 결과
     * @param status   거래 상태
     * @param authUser 로그인 유저
     */
    private void afterSell(Object result, TradingStatus status, AuthUser authUser) {
        status.getOpMode().set(false);
        status.getHold().set(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) result;
        Map<String, Object> order = orders.get(0);
        Double executedFunds = Double.parseDouble((String) order.get("executed_funds"));
        Double paidFee = Double.parseDouble((String) order.get("paid_fee"));
        double sellLocked = Math.round(executedFunds - paidFee);
        double locked = status.getBuyPrice().get();
        double ror = Math.round((sellLocked - locked) / locked * 10.0) / 10.0;
        log.info("{}의 매도 수익률: {}%", authUser.getUserId(), ror);
    }

    public void asyncTest() throws InterruptedException {
        processBuy(); // 비동기 매수 실행
        Thread.sleep(5000);
        log.info("쓰레드슬립");
        processSell();
    }
}
