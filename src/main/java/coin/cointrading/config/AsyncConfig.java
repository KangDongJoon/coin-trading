package coin.cointrading.config;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.dto.TradingStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;

@Configuration
@EnableAsync
public class AsyncConfig {

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

    @Bean
    ConcurrentHashMap<String, TradingStatus> userStatusMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    ConcurrentHashMap<String, AuthUser> userAuthMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public ExecutorService executorService() {
        return Executors.newFixedThreadPool(10);
    }
}

