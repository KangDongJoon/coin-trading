package coin.cointrading.service;

import coin.cointrading.dto.TradingStatus;
import coin.cointrading.exception.CustomException;
import coin.cointrading.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final UpbitCandleService upbitCandleService;
    private final ConcurrentHashMap<String, TradingStatus> userStatusMap;

    @Scheduled(fixedRate = 1000)
    public void updatePriceCache() throws IOException {
        double currentPrice = upbitCandleService.current();
        redisTemplate.opsForValue().set("CURRENT_PRICE", String.valueOf(currentPrice), Duration.ofSeconds(3));
    }

    public double getCurrentPrice() {
        String cachedPrice = redisTemplate.opsForValue().get("CURRENT_PRICE");
        if (cachedPrice != null) {
            return Double.parseDouble(cachedPrice);
        }
        throw new CustomException(ErrorCode.REDIS_NOT_FOUND);
    }

    // second, minute, hour, day of month, month, day of week
    @Scheduled(cron = "20 0 9 * * ?")
    public void updateTargetPrice() throws IOException {
        double targetPrice = upbitCandleService.checkTarget();
        redisTemplate.opsForValue().set("TARGET_PRICE", String.valueOf(targetPrice), Duration.ofHours(23));
        log.info("목표가 갱신: {}", targetPrice);
        for (String userId : userStatusMap.keySet()) {
            TradingStatus status = userStatusMap.get(userId);
            if(!status.getOpMode().get()){
                status.getOpMode().set(true);
                log.info("{}의 op_mode 활성화", userId);
            }
        }
    }

    public double getTargetPrice() {
        String targetPrice = redisTemplate.opsForValue().get("TARGET_PRICE");
        if (targetPrice != null) {
            return Double.parseDouble(targetPrice);
        }
        throw new CustomException(ErrorCode.REDIS_NOT_FOUND);
    }

}
