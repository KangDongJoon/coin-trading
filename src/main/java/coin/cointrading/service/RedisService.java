package coin.cointrading.service;

import coin.cointrading.domain.Coin;
import coin.cointrading.dto.TradingStatus;
import coin.cointrading.exception.CustomException;
import coin.cointrading.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final UpbitCandleService upbitCandleService;
    private final ConcurrentHashMap<String, TradingStatus> userStatusMap;
    private final BackDataService backDataService;
    private final SchedulerControlService schedulerControlService;
    @Getter
    private final Map<Coin, Double> currentPriceMap;
    @Getter
    private final Map<Coin, Double> targetPriceMap;
    @Getter
    private final Map<Coin, Boolean> todayTradeCheckMap;
    @Getter
    private final Map<Coin, Boolean> todayExecutedCheckMap;



    @Scheduled(initialDelay = 3000, fixedDelay = Long.MAX_VALUE)
    public void initialize() throws IOException {
        updatePriceCache();
        updateTargetPrice();
        targetPriceLog();
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

    @Scheduled(fixedDelay = 1000)
    public void updatePriceCache() throws IOException {
        try {

            for (Coin coin : Coin.values()) {
                String currentPriceRedisKey = "CURRENT_PRICE_" + coin;
                Double currentPrice = upbitCandleService.current(coin);
                redisTemplate.opsForValue().set(currentPriceRedisKey, String.valueOf(currentPrice), Duration.ofSeconds(3));
                this.currentPriceMap.put(coin, currentPrice);
            }
        } catch (Exception e) {
            log.error("{}", e.getMessage());
            throw e;
        }
    }

    @Scheduled(cron = "20 0 9 * * *")
    public void updateTargetPrice() {
        try {
            schedulerControlService.setIsProcessing(true);
            log.info("🔴 목표가 갱신 중... checkPrice 멈춤");
            try {
                for (Coin coin : Coin.values()) {
                    double targetPriceCoin = upbitCandleService.checkTarget(coin);
                    targetPriceMap.put(coin, targetPriceCoin);

                    String targetPriceRedisKey = "TARGET_PRICE_" + coin;
                    redisTemplate.opsForValue().set(targetPriceRedisKey, String.valueOf(targetPriceCoin), Duration.ofDays(2));
                }
            } catch (Exception e) {
                log.error("⚠️ 목표가 가져오기 실패 - {}", e.getMessage());
                throw new CustomException(ErrorCode.REDIS_TARGET_PRICE_NOT_FOUND);
            }

            // 금일 거래 및 손절 여부 초기화
            for (Coin coin : Coin.values()) {
                todayTradeCheckMap.put(coin, false);
                todayExecutedCheckMap.put(coin, false);
            }
            log.info("✅ 매수 여부 초기화");

            for (String userId : userStatusMap.keySet()) {
                TradingStatus status = userStatusMap.get(userId);
                if (!status.getOpMode().get()) {
                    status.getOpMode().set(true);
                    log.info("🔹 {}의 op_mode 활성화", userId);
                }
            }
        } finally {
            schedulerControlService.setIsProcessing(false);
            log.info("🟢 목표가 갱신 완료! checkPrice 재개");
        }
    }

    @Scheduled(cron = "0 10 9 * * *")
    public void updateDailyBackData() {
        backDataService.getData("3");
    }

    public void setTodayTrade(Coin coin, Boolean flag) {
        todayTradeCheckMap.put(coin, flag);
    }

    public void setTodayExecuted(Coin coin, Boolean flag) {
        todayExecutedCheckMap.put(coin, flag);
    }

    private void targetPriceLog() {
        log.info("====== 목표가 갱신 ======");
        for (Coin coin : Coin.values()) {
            String formattedPrice = String.format("%,.0f", targetPriceMap.get(coin));
            log.info("{}: {}", coin.getKoreanName(), formattedPrice);
        }
        log.info("======================");
    }
}
