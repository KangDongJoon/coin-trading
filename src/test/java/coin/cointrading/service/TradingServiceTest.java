package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.dto.OrderResponse;
import coin.cointrading.dto.TradingStatus;
import coin.cointrading.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TradingServiceTest {

    @Mock
    private RedisService redisService;

    @Mock
    private UpbitService upbitService;

    private TaskScheduler taskScheduler;
    private ConcurrentHashMap<String, ScheduledFuture<?>> userScheduledTasks;
    private ConcurrentHashMap<String, TradingStatus> userStatusMap;
    private ConcurrentHashMap<String, AuthUser> userAuthMap;
    private ExecutorService executorService;

    @InjectMocks
    private TradingService tradingService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("TradingScheduler-");
        scheduler.initialize();
        taskScheduler = scheduler;
        userScheduledTasks = new ConcurrentHashMap<>();
        userStatusMap = new ConcurrentHashMap<>();
        userAuthMap = new ConcurrentHashMap<>();
        executorService = Executors.newFixedThreadPool(10);

        tradingService = new TradingService(
                taskScheduler,
                userScheduledTasks,
                userStatusMap,
                userAuthMap,
                executorService,
                upbitService,
                redisService
        );
    }

    @Test
    void trading_async() {
        // given
        AuthUser authUser1 = new AuthUser("user1", "nick1", "secret1", "access1");
        AuthUser authUser2 = new AuthUser("user2", "nick2", "secret2", "access2");

        // when && then
        tradingService.startTrading(authUser1);
        tradingService.startTrading(authUser2);
        assertThat(userScheduledTasks.size()).isEqualTo(2);

        tradingService.stopTrading(authUser1);
        assertThat(userScheduledTasks.size()).isEqualTo(1);
        assertThat(userScheduledTasks.containsKey(authUser1.getUserId())).isFalse();

        tradingService.stopTrading(authUser2);
        assertThat(userScheduledTasks.size()).isEqualTo(0);
    }

    @Test
    void startTrading_fail_already_generate() {
        // given
        AuthUser authUser1 = new AuthUser("user1", "nick1", "secret1", "access1");
        tradingService.startTrading(authUser1);

        // when
        CustomException exception = assertThrows(CustomException.class, () -> tradingService.startTrading(authUser1));

        // then
        assertThat(exception.getMessage()).isEqualTo("이미 프로그램이 동작중입니다.");
    }

    @Test
    void startProgram_success_buy() throws Exception {
        AuthUser authUser1 = new AuthUser("user1", "nick1", "secret1", "access1");
        TradingStatus status = new TradingStatus();
        status.getOpMode().set(true);
        userStatusMap.put(authUser1.getUserId(), status);
        double current1 = 10000;
        double current2 = 11000;
        double todayTarget = 11000;
        when(redisService.getCurrentPrice()).thenReturn(current1).thenReturn(current2);
        when(redisService.getTargetPrice()).thenReturn(todayTarget);

        OrderResponse orderResponse = new OrderResponse(
                "KRW_ETH",
                "bid",
                "11000",
                "1.0",
                "1.0",
                "11000"
        );
        when(upbitService.orderCoins("buy", authUser1)).thenReturn(orderResponse);

        for (int i = 0; i < 3; i++){
            tradingService.startProgram(authUser1);
            if(i == 0){
                assertThat(status.getHold()).isFalse();
            }
            if(i == 1){
                assertThat(status.getHold()).isTrue();
            }
        }
        verify(upbitService, times(1)).orderCoins("buy", authUser1);
    }
}




