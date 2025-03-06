package coin.cointrading.config;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.dto.TradingStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    Set<String> runningUser() {
        return ConcurrentHashMap.newKeySet();
    }
}

