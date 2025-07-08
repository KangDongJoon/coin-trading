package coin.cointrading.config;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.domain.BackData;
import coin.cointrading.domain.Coin;
import coin.cointrading.dto.TradingStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    ConcurrentHashMap<String, TradingStatus> userStatusMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    ConcurrentHashMap<String, AuthUser> userAuthMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    ConcurrentHashMap<Coin, List<BackData>> backDataMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    Set<String> runningUser() {
        return ConcurrentHashMap.newKeySet();
    }

    @Bean
    ExecutorService executor() {
        return Executors.newFixedThreadPool(10);
    }
}

