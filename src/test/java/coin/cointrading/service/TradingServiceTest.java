package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.repository.BackDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TradingServiceTest {

    private ConcurrentHashMap<String, Future<?>> userTrades;
    private ExecutorService executorService;
    private Map<String, AtomicBoolean> userRunningStatus;

    @Mock
    private BackDataRepository backDataRepository;
    @Mock
    private UpbitService upbitService;

    @InjectMocks
    private TradingService tradingService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        userTrades = new ConcurrentHashMap<>();
        executorService = Executors.newFixedThreadPool(2); // 2개의 스레드 풀 생성
        userRunningStatus = new ConcurrentHashMap<>();
        tradingService = new TradingService(userTrades, executorService, userRunningStatus, backDataRepository, upbitService);
    }

    @Test
    void startTrading_multipleThreads() {
        // given
        AuthUser authUser1 = new AuthUser("user1", "nick1", "secret1", "access1");
        AuthUser authUser2 = new AuthUser("user2", "nick2", "secret2", "access2");

        // when
        executorService.execute(() -> tradingService.startTrading(authUser1));
        executorService.execute(() -> tradingService.startTrading(authUser2));

        // then
        assertThat(userTrades).hasSize(2);
        assertThat(userRunningStatus.get("user1").get()).isTrue();
        assertThat(userRunningStatus.get("user2").get()).isTrue();
    }

    @Test
    void stopTrading() throws Exception {
        // given
        AuthUser authUser1 = new AuthUser("user1", "nick1", "secret1", "access1");
        AuthUser authUser2 = new AuthUser("user2", "nick2", "secret2", "access2");

        // when & then
        // 프로그램 2개 동작
        executorService.execute(() -> tradingService.startTrading(authUser1));
        Thread.sleep(1000);
        assertThat(userTrades).hasSize(1);
        executorService.execute(() -> tradingService.startTrading(authUser2));
        Thread.sleep(1000);
        assertThat(userTrades).hasSize(2);
        assertThat(userRunningStatus.get("user1").get()).isTrue();
        assertThat(userRunningStatus.get("user2").get()).isTrue();

        // 프로그램1 종료(실행 1 종료 1)
        tradingService.stopTrading(authUser1);
        assertThat(userTrades).hasSize(1);
        assertThat(userRunningStatus.get("user1").get()).isFalse();
        assertThat(userRunningStatus.get("user2").get()).isTrue();

        // 프로그램2 종료(실행 0 종료 2)
        tradingService.stopTrading(authUser2);
        assertThat(userTrades).hasSize(0);
        assertThat(userRunningStatus.get("user1").get()).isFalse();
        assertThat(userRunningStatus.get("user2").get()).isFalse();
    }
}
