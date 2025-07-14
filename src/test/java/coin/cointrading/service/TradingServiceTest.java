package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.domain.Coin;
import coin.cointrading.dto.TradingStatus;
import coin.cointrading.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingServiceTest {

    @Spy
    private ConcurrentHashMap<String, TradingStatus> userStatusMap; // 유저 거래상태 저장 컬렉션
    @Spy
    private ConcurrentHashMap<String, AuthUser> userAuthMap; // 유저 Auth정보 저장 컬렉션
    @Spy
    private Set<String> runningUser;
    @Mock
    private SchedulerControlService schedulerControlService;
    @Mock
    private UpbitService upbitService;
    @Mock
    private RedisService redisService;
    @Mock
    private ExecutorService executor;
    @Mock
    private UserRepository userRepository;

    TradingService tradingService;

    private AuthUser authUser;
    private String strCoin;
    private Coin coin;

    @BeforeEach
    void setup() {
        userAuthMap = spy(new ConcurrentHashMap<>());
        userStatusMap = spy(new ConcurrentHashMap<>());
        runningUser = spy(ConcurrentHashMap.newKeySet());

        tradingService = new TradingService(
                userStatusMap,
                userAuthMap,
                runningUser,
                schedulerControlService,
                upbitService,
                redisService,
                executor,
                userRepository
        );

        authUser = new AuthUser("test", "nickName");
        strCoin = "BTC";
        coin = Coin.valueOf(strCoin);
    }

    @Test
    void startTrading_success() {
        // give & when
        tradingService.startTrading(authUser, strCoin);

        // then
        verify(userAuthMap).putIfAbsent(eq(authUser.getUserId()), any(AuthUser.class));
        assertThat(userAuthMap.get("test")).isEqualTo(authUser);

        verify(userStatusMap).putIfAbsent(eq(authUser.getUserId()), any(TradingStatus.class));
        assertThat(userStatusMap.get("test").getSelectCoin()).isEqualTo(coin);

        verify(runningUser).add(authUser.getUserId());
        assertThat(runningUser).contains("test");
    }

    @Test
    void stopTrading_success() {
        // given
        tradingService.startTrading(authUser, strCoin);
        assertThat(userAuthMap.get("test")).isEqualTo(authUser);
        assertThat(userStatusMap.get("test").getSelectCoin()).isEqualTo(coin);
        assertThat(runningUser).contains("test");

        // when
        tradingService.stopTrading(authUser);

        // then
        verify(runningUser).remove(authUser.getUserId());
        assertThat(runningUser.contains("test")).isFalse();

        verify(userAuthMap).remove(authUser.getUserId());
        assertThat(userAuthMap.get("test")).isNull();

        verify(userStatusMap).remove(authUser.getUserId());
        assertThat(userStatusMap.get("test")).isNull();
    }

    @Test
    void checkStatus_success() {
        // given
        runningUser.add(authUser.getUserId());

        // when
        String result = tradingService.checkStatus(authUser);

        // then
        assertThat(runningUser.contains(authUser.getUserId())).isTrue();
        assertThat(result).isEqualTo("true");
    }

    @Test
    void programStatus_logsStatusOfRunningUsers() {
        // given
        TradingStatus status = new TradingStatus(coin);
        status.getOpMode().set(true);
        status.getHold().set(true);
        runningUser.add(authUser.getUserId());
        userStatusMap.put(authUser.getUserId(), status);


        // when
        tradingService.programStatus();

        // then
        assertThat(userStatusMap.get(authUser.getUserId()).getSelectCoin()).isEqualTo(Coin.BTC);
        assertThat(userStatusMap.get(authUser.getUserId()).getOpMode()).isTrue();
        assertThat(userStatusMap.get(authUser.getUserId()).getHold().get()).isTrue();
    }

    @Test
    void checkPrice_underTargetPrice() {
        // given
        Map<Coin, Double> currentPriceMap = new HashMap<>();
        currentPriceMap.put(Coin.BTC, 10000d);
        currentPriceMap.put(Coin.ETH, 1000d);
        currentPriceMap.put(Coin.XRP, 100d);
        Map<Coin, Double> targetPriceMap = new HashMap<>();
        targetPriceMap.put(Coin.BTC, 20000d);
        targetPriceMap.put(Coin.ETH, 2000d);
        targetPriceMap.put(Coin.XRP, 200d);

        Map<Coin, Boolean> todayTradeCheckMap = new HashMap<>();
        todayTradeCheckMap.put(Coin.BTC, false);
        todayTradeCheckMap.put(Coin.ETH, false);
        todayTradeCheckMap.put(Coin.XRP, false);

        when(redisService.getCurrentPriceMap()).thenReturn(currentPriceMap);
        when(redisService.getTargetPriceMap()).thenReturn(targetPriceMap);
        when(redisService.getTodayTradeCheckMap()).thenReturn(todayTradeCheckMap);
        when(schedulerControlService.getIsProcessing()).thenReturn(false);

        // when
        tradingService.checkPrice();

        // then
        verify(redisService, never()).setTodayTrade(eq(Coin.ETH), eq(true));
        verify(redisService, never()).setTodayTrade(eq(Coin.XRP), eq(true));
        verify(redisService, never()).setTodayTrade(eq(Coin.BTC), eq(true));
    }

    @Test
    void checkPrice_overTargetPrice_triggersProcessBuy() {
        // given
        Map<Coin, Double> currentPriceMap = new HashMap<>();
        currentPriceMap.put(Coin.BTC, 20000d);
        currentPriceMap.put(Coin.ETH, 2000d);
        currentPriceMap.put(Coin.XRP, 200d);
        Map<Coin, Double> targetPriceMap = new HashMap<>();
        targetPriceMap.put(Coin.BTC, 20000d);
        targetPriceMap.put(Coin.ETH, 2000d);
        targetPriceMap.put(Coin.XRP, 200d);

        Map<Coin, Boolean> todayTradeCheckMap = new HashMap<>();
        todayTradeCheckMap.put(Coin.BTC, false);
        todayTradeCheckMap.put(Coin.ETH, false);
        todayTradeCheckMap.put(Coin.XRP, false);

        when(redisService.getCurrentPriceMap()).thenReturn(currentPriceMap);
        when(redisService.getTargetPriceMap()).thenReturn(targetPriceMap);
        when(redisService.getTodayTradeCheckMap()).thenReturn(todayTradeCheckMap);
        when(schedulerControlService.getIsProcessing()).thenReturn(false);

        // when
        tradingService.checkPrice();

        // then
        verify(redisService).setTodayTrade(eq(Coin.BTC), eq(true));
        verify(redisService).setTodayTrade(eq(Coin.ETH), eq(true));
        verify(redisService).setTodayTrade(eq(Coin.XRP), eq(true));
    }

    @Test
    void checkPrice_underExecutePrice_triggersStopLoss() {
        // given
        Map<Coin, Double> currentPriceMap = new HashMap<>();
        currentPriceMap.put(Coin.BTC, 9500d);
        currentPriceMap.put(Coin.ETH, 950d);
        currentPriceMap.put(Coin.XRP, 95d);
        Map<Coin, Double> targetPriceMap = new HashMap<>();
        targetPriceMap.put(Coin.BTC, 10000d);
        targetPriceMap.put(Coin.ETH, 1000d);
        targetPriceMap.put(Coin.XRP, 100d);

        Map<Coin, Boolean> todayTradeCheckMap = new HashMap<>();
        todayTradeCheckMap.put(Coin.BTC, true);
        todayTradeCheckMap.put(Coin.ETH, true);
        todayTradeCheckMap.put(Coin.XRP, true);

        Map<Coin, Boolean> todayExecutedCheckMap = new HashMap<>();
        todayExecutedCheckMap.put(Coin.BTC, false);
        todayExecutedCheckMap.put(Coin.ETH, false);
        todayExecutedCheckMap.put(Coin.XRP, false);



        when(redisService.getCurrentPriceMap()).thenReturn(currentPriceMap);
        when(redisService.getTargetPriceMap()).thenReturn(targetPriceMap);
        when(redisService.getTodayTradeCheckMap()).thenReturn(todayTradeCheckMap);
        when(redisService.getTodayExecutedCheckMap()).thenReturn(todayExecutedCheckMap);
        when(schedulerControlService.getIsProcessing()).thenReturn(false);

        // when
        tradingService.checkPrice();

        // then
        verify(redisService).setTodayExecuted(eq(Coin.BTC), eq(true));
        verify(redisService).setTodayExecuted(eq(Coin.ETH), eq(true));
        verify(redisService).setTodayExecuted(eq(Coin.XRP), eq(true));
    }
}