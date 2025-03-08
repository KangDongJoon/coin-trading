package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.dto.OrderResponse;
import coin.cointrading.dto.TradingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class TradingServiceTest {

    @Mock
    private RedisService redisService;

    @Mock
    private UpbitService upbitService;

    private ConcurrentHashMap<String, TradingStatus> userStatusMap;
    private ConcurrentHashMap<String, AuthUser> userAuthMap;
    private Set<String> runningUser;

    private AuthUser authUser1;
    private AuthUser authUser2;

    @InjectMocks
    private TradingService tradingService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("TradingScheduler-");
        scheduler.initialize();
        userStatusMap = new ConcurrentHashMap<>();
        userAuthMap = new ConcurrentHashMap<>();
        runningUser = ConcurrentHashMap.newKeySet();

        tradingService = new TradingService(
                userStatusMap,
                userAuthMap,
                runningUser,
                upbitService,
                redisService
        );

        authUser1 = new AuthUser("user1", "nick1", "secret1", "access1");
        authUser2 = new AuthUser("user2", "nick2", "secret2", "access2");
    }

    @Test
    void startTrading_success() {
        // when & then
        tradingService.startTrading(authUser1);
        assertThat(runningUser.size()).isEqualTo(1);
        assertTrue(userStatusMap.containsKey(authUser1.getUserId()));
        assertTrue(userAuthMap.containsKey(authUser1.getUserId()));
        tradingService.startTrading(authUser2);
        assertThat(runningUser.size()).isEqualTo(2);
        assertTrue(userStatusMap.containsKey(authUser2.getUserId()));
        assertTrue(userAuthMap.containsKey(authUser2.getUserId()));
    }

    @Test
    void stopTrading_success() {
        // given
        tradingService.startTrading(authUser1);
        tradingService.startTrading(authUser2);
        // when & then
        assertThat(runningUser.size()).isEqualTo(2);
        tradingService.stopTrading(authUser1);
        assertThat(runningUser.size()).isEqualTo(1);
        assertFalse(runningUser.contains(authUser1.getUserId()));
        tradingService.stopTrading(authUser2);
        assertTrue(runningUser.isEmpty());
    }

    @Test
    void checkPrice_processBuy_success() throws Exception {
        // given
        when(redisService.getCurrentPrice()).thenReturn(1600000d);
        when(redisService.getTargetPrice()).thenReturn(1500000d);
        runningUser.add(authUser1.getUserId());
        userAuthMap.put(authUser1.getUserId(), authUser1);
        TradingStatus status = new TradingStatus();
        status.getOpMode().set(true);
        userStatusMap.put(authUser1.getUserId(), status);
        Object orderResponse = new OrderResponse(
                "KRW-ETH",
                "bid",
                "1600000",
                "1.0",
                "1.0",
                "1600000"
        );
        when(upbitService.orderCoins("buy", authUser1)).thenReturn(orderResponse);

        // when
        tradingService.checkPrice();
        await()
                .atMost(3, TimeUnit.SECONDS)
                .until(() -> status.getHold().get());

        // then
        assertTrue(status.getHold().get());
    }

    @Test
    void checkPrice_processExecutedFunds_success() throws Exception {
        // given
        when(redisService.getCurrentPrice()).thenReturn(800000d);
        when(redisService.getTargetPrice()).thenReturn(1000000d);
        runningUser.add(authUser1.getUserId());
        userAuthMap.put(authUser1.getUserId(), authUser1);
        TradingStatus status = new TradingStatus();
        status.getOpMode().set(true);
        status.getHold().set(true);
        userStatusMap.put(authUser1.getUserId(), status);

        Object orderResponse = new OrderResponse(
                "KRW-ETH",
                "bid",
                "800000",
                "1.0",
                "1.0",
                "800000"
        );
        when(upbitService.orderCoins("sell", authUser1)).thenReturn(orderResponse);
        when(upbitService.getOrders(authUser1, 1)).thenReturn(orderResponse);

        // when
        tradingService.checkPrice();

        // then
        assertTrue(status.getStopLossExecuted().get());
    }
}




