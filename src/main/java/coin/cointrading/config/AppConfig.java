package coin.cointrading.config;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.dto.TradingStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Configuration
public class AppConfig {

    @Bean
    public ConcurrentHashMap<String, Future<?>> userTrades() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public ExecutorService executorService() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    public Map<String, AtomicBoolean> userRunningStatus() {
        return new HashMap<>();
    }

    @Bean
    ConcurrentHashMap<String, TradingStatus> userStatusMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    ConcurrentHashMap<String, AuthUser> userAuthMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("TradingScheduler-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    ConcurrentHashMap<String, ScheduledFuture<?>> userScheduledTasks() {
        return new ConcurrentHashMap<>();
    }

}
