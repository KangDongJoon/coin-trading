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
    private final Map<String, Double> currentPrice;

    @PostConstruct
    public void initialize() throws IOException {
        updatePriceCache();
        double targetPrice_BTC = upbitCandleService.checkTarget("BTC");
        redisTemplate.opsForValue().set("TARGET_PRICE_BTC", String.valueOf(targetPrice_BTC), Duration.ofDays(2));
        double targetPrice_ETH = upbitCandleService.checkTarget("ETH");
        redisTemplate.opsForValue().set("TARGET_PRICE_ETH", String.valueOf(targetPrice_ETH), Duration.ofDays(2));
        double targetPrice_XRP = upbitCandleService.checkTarget("XRP");
        redisTemplate.opsForValue().set("TARGET_PRICE_XRP", String.valueOf(targetPrice_XRP), Duration.ofDays(2));

        targetPrcieLog(targetPrice_BTC, targetPrice_ETH, targetPrice_XRP, "----- 목표가 갱신 -----", "--------------------");
        setTodayTradeCheck("false");
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
        double currentPrice_BTC = upbitCandleService.current("BTC");
        double currentPrice_ETH = upbitCandleService.current("ETH");
        double currentPrice_XRP = upbitCandleService.current("XRP");

        redisTemplate.opsForValue().set("CURRENT_PRICE_BTC", String.valueOf(currentPrice_BTC));
        redisTemplate.opsForValue().set("CURRENT_PRICE_ETH", String.valueOf(currentPrice_ETH));
        redisTemplate.opsForValue().set("CURRENT_PRICE_XRP", String.valueOf(currentPrice_XRP));

        redisTemplate.expire("CURRENT_PRICE_BTC", Duration.ofSeconds(3));
        redisTemplate.expire("CURRENT_PRICE_ETH", Duration.ofSeconds(3));
        redisTemplate.expire("CURRENT_PRICE_XRP", Duration.ofSeconds(3));
    }


    public Map<String, Double> getCurrentPrice() {
        String cachedPrice_BTC = redisTemplate.opsForValue().get("CURRENT_PRICE_BTC");
        String cachedPrice_ETH = redisTemplate.opsForValue().get("CURRENT_PRICE_ETH");
        String cachedPrice_XRP = redisTemplate.opsForValue().get("CURRENT_PRICE_XRP");
        if (cachedPrice_BTC != null && cachedPrice_ETH != null && cachedPrice_XRP != null) {
            currentPrice.clear();
            currentPrice.put("BTC", Double.parseDouble(cachedPrice_BTC));
            currentPrice.put("ETH", Double.parseDouble(cachedPrice_ETH));
            currentPrice.put("XRP", Double.parseDouble(cachedPrice_XRP));
            return currentPrice;
        }
        log.error("{}", ErrorCode.REDIS_NOT_FOUND.getMessage());
        throw new CustomException(ErrorCode.REDIS_NOT_FOUND);
    }

    @Scheduled(cron = "20 0 9 * * *")
    public void updateTargetPrice() {
        double targetPrice_BTC = -1;
        double targetPrice_ETH = -1;
        double targetPrice_XRP = -1;

        try {
            schedulerControlService.setIsProcessing(true);
            log.info("🔴 목표가 갱신 중... checkPrice 멈춤");
            try {
                targetPrice_BTC = upbitCandleService.checkTarget("BTC");
                targetPrice_ETH = upbitCandleService.checkTarget("ETH");
                targetPrice_XRP = upbitCandleService.checkTarget("XRP");
            } catch (Exception e) {
                log.error("⚠️ 목표가 가져오기 실패 - {}", e.getMessage());
            }

            // 목표가가 정상적으로 설정되지 않았다면 종료
            if (targetPrice_BTC < 0 || targetPrice_ETH < 0 || targetPrice_XRP < 0) {
                throw new CustomException(ErrorCode.REDIS_TARGET_PRICE_NOT_FOUND);
            }

            redisTemplate.opsForValue().set("TARGET_PRICE_BTC", String.valueOf(targetPrice_BTC), Duration.ofDays(2));
            redisTemplate.opsForValue().set("TARGET_PRICE_ETH", String.valueOf(targetPrice_ETH), Duration.ofDays(2));
            redisTemplate.opsForValue().set("TARGET_PRICE_XRP", String.valueOf(targetPrice_XRP), Duration.ofDays(2));
            setTodayTradeCheck("false");

            targetPrcieLog(targetPrice_BTC, targetPrice_ETH, targetPrice_XRP, "✅ 목표가 갱신 완료 -----", "----------------------");
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
        if (targetPrice == null) {
            throw new CustomException(ErrorCode.REDIS_TARGET_PRICE_NOT_FOUND);
        }
        return Double.parseDouble(targetPrice);
    }

    @Scheduled(cron = "0 10 9 * * *")
    public void updateDailyBackData() {
        backDataService.getData("3");
    }

    public void setTodayTradeCheck(String flag) {
        redisTemplate.opsForValue().set("TODAY_TRADE", flag, Duration.ofDays(2));
    }

    public String getTodayTradeCheck() {
        return redisTemplate.opsForValue().get("TODAY_TRADE");
    }

    private static void targetPrcieLog(double targetPrice_BTC, double targetPrice_ETH, double targetPrice_XRP, String s, String s1) {
        String formattedPrice_BTC = String.format("%,.0f", targetPrice_BTC);
        String formattedPrice_ETH = String.format("%,.0f", targetPrice_ETH);
        String formattedPrice_XRP = String.format("%,.0f", targetPrice_XRP);
        log.info(s);
        log.info("비트코인: {}", formattedPrice_BTC);
        log.info("이더리움: {}", formattedPrice_ETH);
        log.info("리   플: {}", formattedPrice_XRP);
        log.info(s1);
    }
}
