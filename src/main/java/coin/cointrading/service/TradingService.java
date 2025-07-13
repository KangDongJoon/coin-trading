package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.domain.Coin;
import coin.cointrading.domain.User;
import coin.cointrading.dto.OrderResponse;
import coin.cointrading.dto.TradingStatus;
import coin.cointrading.exception.CustomException;
import coin.cointrading.exception.ErrorCode;
import coin.cointrading.repository.UserRepository;
import lombok.Getter;
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

    @Getter
    private final ConcurrentHashMap<String, TradingStatus> userStatusMap; // 유저 거래상태 저장 컬렉션
    private final ConcurrentHashMap<String, AuthUser> userAuthMap; // 유저 Auth정보 저장 컬렉션
    private final Set<String> runningUser; // 현재 프로그램을 실행중인 유저를 저장하는 컬렉션
    private final SchedulerControlService schedulerControlService;
    private final UpbitService upbitService;
    private final RedisService redisService;
    private final ExecutorService executor;
    private final UserRepository userRepository;

    /**
     * 프로그램 실행
     *
     * @param authUser 로그인 유저
     */
    public void startTrading(AuthUser authUser, String strCoin) {
        Coin coin = Coin.valueOf(strCoin);

        initProgram(authUser, coin);
        runningUser.add(authUser.getUserId());
        log.info("==== {}의 프로그램이 실행되었습니다 =====", authUser.getUserId());
        log.info("선택 코인: {}", coin.getKoreanName());
        String formattedPrice_Coin = String.format("%,.0f", redisService.getTargetPriceMap().get(coin));
        log.info("금일 목표가 : {}원", formattedPrice_Coin);
    }

    /**
     * 프로그램 종료, 서버에서 상태 및 Auth 정보도 함께 삭제
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
    private void initProgram(AuthUser authUser, Coin selectCoin) {
        userAuthMap.putIfAbsent(authUser.getUserId(), authUser);
        userStatusMap.putIfAbsent(authUser.getUserId(), new TradingStatus(selectCoin));
    }

    /**
     * 1시간마다 유저들의 프로그램 동작상태 확인
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void programStatus() {
        for (String userId : runningUser) {
            TradingStatus status = userStatusMap.get(userId);
            log.info("====== {}의 프로그램 동작중 ======", userId);
            log.info("동작상태: {}", status.getOpMode());
            log.info("코인종류: {}", status.getSelectCoin());
            log.info("매수여부: {}", status.getHold().get());
        }
    }

    /**
     * 1초마다 코인 시세 확인 후 매수, 손절 진행
     */
    @Scheduled(initialDelay = 10000, fixedDelay = 1000)
    public void checkPrice() {
        if (schedulerControlService.getIsProcessing()) {
            return;
        }

        schedulerControlService.setIsProcessing(true); // 🔹 실행 시작 표시

        try {
            for (Coin coin : Coin.values()) {
                Double currentPrice = redisService.getCurrentPriceMap().get(coin);
                Double targetPrice = redisService.getTargetPriceMap().get(coin);
                String todayTradeCheck = redisService.getTodayTradeCheckMap().get(coin);

                // 조건 매수
                if (todayTradeCheck.equals("false")) {
                    if (currentPrice >= targetPrice) {
                        processBuy(coin)
                                .thenRun(() -> schedulerControlService.setIsProcessing(false));  // 🔹 비동기 완료 후 해제
                    }
                }

                // 손절
                if (todayTradeCheck.equals("true") && currentPrice <= targetPrice * 0.95) {
                    processExecute();
                } else {
                    schedulerControlService.setIsProcessing(false);
                }
            }
        } catch (Exception e) {
            log.error("🚨 checkPrice() 실행 중 오류 발생: {} 스케쥴링 중지", e.getMessage());
        }
    }

    /**
     * 조건에 부합 시 매수 진행
     */
    private CompletableFuture<Void> processBuy(Coin buyCoin) {
        log.info("====== 매수 로직 실행 중 ======");
        log.info("매수 종목: {}", buyCoin);

        List<CompletableFuture<Void>> futures = runningUser.stream() // 실행 중인 유저를 돌면서 매수 진행
                .map(userId -> {
                    TradingStatus status = userStatusMap.get(userId);
                    if (status.getOpMode().get() // 1일 후 거래
                            && !status.getStopLossExecuted().get() // 금일 손절 로직 실행 여부
                            && !status.getHold().get()) { // 매수 여부
                        User requestUser = getRequestUserByIdOrThrow(userAuthMap.get(userId));
                        return executeAsyncBuy(requestUser, status);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();

        redisService.setTodayTradeCheck(buyCoin, "true");
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * 비동기 처리로 Upbit 매수 API 요청 및 상태 변경
     * @param requestUser 요청 유저
     * @param status   유저 거래 상태
     */
    private CompletableFuture<Void> executeAsyncBuy(User requestUser, TradingStatus status) {
        return CompletableFuture.supplyAsync(() -> {  // 🔹 'return' 추가
            try {
                return upbitService.orderCoins("buy", requestUser, status.getSelectCoin());
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new CustomException(ErrorCode.UPBIT_ORDER_FAIL);
            }
        }, executor).thenAccept(result -> afterBuy(result, status, requestUser));
    }

    /**
     * 매일 장 종료 시 전량 매도 처리
     */
    @Scheduled(cron = "50 59 8 * * ?")
    public void processSell() {
        for (String userId : runningUser) { // 실행중인 유저 확인
            TradingStatus status = userStatusMap.get(userId);
            if (status.getOpMode().get() // 동작 상태 확인
                    && !status.getStopLossExecuted().get() // 손절 여부 확인
                    && status.getHold().get()) { // 매수 여부 확인
                User requestUser = getRequestUserByIdOrThrow(userAuthMap.get(userId));
                executeAsyncSell(requestUser, status);
            }
        }
    }

    /**
     * 조건에 부합 시 손절 진행
     */
    private void processExecute() {
        log.info("====== 손절 로직 실행 중 ======");

        List<CompletableFuture<Void>> futures = runningUser.stream()
                .map(userId -> {
                    TradingStatus status = userStatusMap.get(userId);
                    if (status.getOpMode().get() && !status.getStopLossExecuted().get() && status.getHold().get()) {
                        User requestUser = getRequestUserByIdOrThrow(userAuthMap.get(userId));
                        status.getStopLossExecuted().set(true);
                        return executeAsyncSell(requestUser, status);
                    }
                    return null;
                })
                .filter(Objects::nonNull) // ✅ null을 제거하여 올바른 CompletableFuture 리스트 생성
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }


    /**
     * 비동기 처리로 Upbit 매도 API 요청 및 상태 변경
     * @param requestUser 로그인 유저
     * @param status   거래 상태
     */
    private CompletableFuture<Void> executeAsyncSell(User requestUser, TradingStatus status) {
        log.info("====== 매도 로직 실행 중 ======");
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return upbitService.orderCoins("sell", requestUser, status.getSelectCoin());
                    } catch (Exception e) {
                        log.error(e.getMessage());
                        throw new CustomException(ErrorCode.UPBIT_ORDER_FAIL);
                    }
                }, executor
        ).thenCompose(orderResponse ->
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return upbitService.getOrders(requestUser, 2, status.getSelectCoin());
                            } catch (Exception e) {
                                log.error(e.getMessage());
                                throw new CustomException(ErrorCode.UPBIT_ORDER_LIST_READ_FAIL);
                            }
                        }, executor
                ).thenAccept(result -> afterSell(result, status, requestUser))
        );
    }

    /**
     * 매수 처리 이후 상태 변경 및 매수금액 확인
     * @param result   거래 결과
     * @param status   거래 상태
     * @param requestUser 로그인 유저
     */
    private void afterBuy(Object result, TradingStatus status, User requestUser) {
        OrderResponse response = (OrderResponse) result;
        status.getHold().set(true);  // 매수 완료 상태로 변경
        double locked = Math.round(Double.parseDouble(response.getLocked()));

        String formatted_locked = String.format("%,.0f", locked);
        log.info("{}의 매수 금액: {}원(수수료 포함)", requestUser.getUserId(), formatted_locked);
    }

    /**
     * 매도 처리 후 상태 변경 및 수익률 확인
     * 주문 처리 완료 전 주문 API 요청 시 매도에 대한 주문이 없기에 매수 기준으로 수익률 기준
     * @param result   거래 결과
     * @param status   거래 상태
     * @param requestUser 로그인 유저
     */
    private void afterSell(Object result, TradingStatus status, User requestUser) {
        status.getOpMode().set(false);
        status.getHold().set(false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) result;

        Map<String, Object> order = orders.stream()
                .filter(o -> "bid".equals(o.get("side")))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND, "매수"));


        double paid_fee = Double.parseDouble((String) order.get(("paid_fee")));
        double executed_funds = Double.parseDouble((String) order.get(("executed_funds")));
        double executed_volume = Double.parseDouble((String) order.get("executed_volume"));

        double buyPrice = executed_funds + paid_fee;

        double currentPrice = redisService.getCurrentPriceMap().get(status.getSelectCoin());
        double sellPrice = (executed_volume * currentPrice) * 0.9995;

        double ror = (sellPrice - buyPrice) / buyPrice * 100;

        log.info("{}의 매도 수익률: {}", requestUser.getUserId(), String.format("%.1f%%", ror));
    }

    private User getRequestUserByIdOrThrow(AuthUser authUser) {
        return userRepository.findByUserId(authUser.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_USER_NOT_FOUND));
    }

    /**
     * 테스트 메서드들
     */
    public void asyncTest(AuthUser authUser) throws InterruptedException {
        processBuy(userStatusMap.get(authUser.getUserId()).getSelectCoin()); // 비동기 매수 실행
        Thread.sleep(6000);
        log.info("쓰레드슬립");
        processSell();
    }

    public void opChange() {
        for (String userId : runningUser) {
            TradingStatus status = userStatusMap.get(userId);
            status.getOpMode().set(true);
            log.info("{}의 op_mode 변경완료: {}", userId, status.getOpMode().get());
        }
    }

    public void holdChange() {
        for (String userId : runningUser) {
            TradingStatus status = userStatusMap.get(userId);
            status.getHold().set(true);
            log.info("{}의 hold 변경완료: {}", userId, status.getHold().get());
        }
    }
}
