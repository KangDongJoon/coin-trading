package coin.cointrading.service;

import coin.cointrading.dto.TradingStatus;
import coin.cointrading.exception.CustomException;
import coin.cointrading.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final UpbitCandleService upbitCandleService;
    private final ConcurrentHashMap<String, TradingStatus> userStatusMap;

    @PostConstruct
    public void initialize() throws IOException {
        updatePriceCache();
        updateTargetPrice();
    }

    // Refresh Token 저장
    public void saveRefreshToken(String userId, String refreshToken, long durationInSeconds) {
        ValueOperations<String, String> values = redisTemplate.opsForValue();
        values.set("refresh:" + userId, refreshToken, durationInSeconds, TimeUnit.SECONDS);
    }

    // Refresh Token 조회
    public String getRefreshToken(String userId) {
        ValueOperations<String, String> values = redisTemplate.opsForValue();
        return values.get("refresh:" + userId);
    }

    // Refresh Token 삭제 (로그아웃 시)
    public void deleteRefreshToken(String userId) {
        redisTemplate.delete("refresh:" + userId);
    }

    @Scheduled(fixedRate = 1000)
    public void updatePriceCache() throws IOException {
        double currentPrice = upbitCandleService.current();

        redisTemplate.opsForValue().getAndSet("CURRENT_PRICE", String.valueOf(currentPrice));

        redisTemplate.expire("CURRENT_PRICE", Duration.ofSeconds(3));
    }


    public double getCurrentPrice() {
        String cachedPrice = redisTemplate.opsForValue().get("CURRENT_PRICE");
        if (cachedPrice != null) {
            return Double.parseDouble(cachedPrice);
        }
        log.error("{}", ErrorCode.REDIS_NOT_FOUND.getMessage());
        throw new CustomException(ErrorCode.REDIS_NOT_FOUND);
    }


    @Scheduled(cron = "20 0 9 * * ?")
    public void updateTargetPrice() throws IOException {
        double targetPrice = upbitCandleService.checkTarget();
        redisTemplate.opsForValue().set("TARGET_PRICE", String.valueOf(targetPrice), Duration.ofHours(24));
        log.info("목표가 갱신: {}", targetPrice);
        for (String userId : userStatusMap.keySet()) {
            TradingStatus status = userStatusMap.get(userId);
            if (!status.getOpMode().get()) {
                status.getOpMode().set(true);
                status.getStopLossExecuted().set(false);
                log.info("{}의 op_mode 활성화", userId);
            }
        }
    }

    public double getTargetPrice() {
        String targetPrice = redisTemplate.opsForValue().get("TARGET_PRICE");
        if (targetPrice != null) {
            return Double.parseDouble(targetPrice);
        } else {
            log.error("{}", ErrorCode.REDIS_NOT_FOUND.getMessage());
            // 비동기 방식으로 갱신 후 다시 호출하는 방식
            CompletableFuture.runAsync(() -> {
                try {
                    updateTargetPrice();  // 비동기 방식으로 target price 갱신
                } catch (IOException e) {
                    log.error("Target price 갱신 실패", e);
                }
            }).join(); // 갱신이 완료될 때까지 대기

            // 갱신 후 target price 반환
            return getTargetPrice();  // 갱신된 값 반환
        }
    }
}
