package coin.cointrading.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
}
