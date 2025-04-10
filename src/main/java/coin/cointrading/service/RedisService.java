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

    @PostConstruct
    public void initialize() throws IOException {
        updatePriceCache();
        double targetPrice = upbitCandleService.checkTarget();
        redisTemplate.opsForValue().set("TARGET_PRICE", String.valueOf(targetPrice), Duration.ofDays(2));
        setTodayTradeCheck("false");
        log.info("목표가 최초 갱신: {}", targetPrice);
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
        double currentPrice = upbitCandleService.current();

        redisTemplate.opsForValue().set("CURRENT_PRICE", String.valueOf(currentPrice));

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
    public void updateTargetPrice() {
        int maxRetries = 10; // 최대 재시도 횟수
        int attempt = 0;
        double targetPrice = -1;

        try {
            schedulerControlService.setIsProcessing(true);
            log.info("🔴 목표가 갱신 중... checkPrice 멈춤");

            while (targetPrice < 0 && attempt < maxRetries) {
                try {
                    targetPrice = upbitCandleService.checkTarget();
                } catch (Exception e) {
                    attempt++;
                    log.error("⚠️ 목표가 가져오기 실패 ({}번째 시도) - {}", attempt, e.getMessage());
                    if (attempt >= maxRetries) {
                        log.error("🚨 목표가 갱신 실패: 최대 재시도 횟수 초과");
                        return; // 재시도 초과 시 안전 종료
                    }
                }
            }

            // 목표가가 정상적으로 설정되지 않았다면 종료
            if (targetPrice < 0) {
                throw new CustomException(ErrorCode.REDIS_NOT_FOUND);
            }

            redisTemplate.opsForValue().set("TARGET_PRICE", String.valueOf(targetPrice), Duration.ofDays(2));
            setTodayTradeCheck("false");
            log.info("✅ 목표가 갱신 완료: {}", targetPrice);
            log.info("✅ 매수 여부 초기화");

            for (String userId : userStatusMap.keySet()) {
                TradingStatus status = userStatusMap.get(userId);
                if (!status.getOpMode().get()) {
                    status.getOpMode().set(true);
                    status.getStopLossExecuted().set(false);
                    log.info("🔹 {}의 op_mode 활성화", userId);
                }
            }
        } finally {
            schedulerControlService.setIsProcessing(false);
            log.info("🟢 목표가 갱신 완료! checkPrice 재개");
        }
    }


    public double getTargetPrice() {
        String targetPrice = redisTemplate.opsForValue().get("TARGET_PRICE");
        return Double.parseDouble(targetPrice);
    }

    @Scheduled(cron = "0 10 9 * * ?")
    public void updateDailyBackData() {
        backDataService.getData("2");
    }

    public void setTodayTradeCheck(String flag) {
        redisTemplate.opsForValue().set("TODAY_TRADE", flag, Duration.ofDays(2));
    }

    public String getTodayTradeCheck() {
        return redisTemplate.opsForValue().get("TODAY_TRADE");
    }
}
