package coin.cointrading.service;

import coin.cointrading.domain.BackData;
import coin.cointrading.domain.Coin;
import coin.cointrading.dto.UpbitCandle;
import coin.cointrading.repository.BackDataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BackDataService {

    private final BackDataRepository backDataRepository;
    private final OkHttpClient okHttpClient;
    @Getter
    private final ConcurrentHashMap<Coin, List<BackData>> backDataMap;

    @PostConstruct
    public void postBackData() {
        for (Coin coin : Coin.values()) {
            LocalDate today = LocalDate.now();
            Pageable pageable = PageRequest.of(0, 1); // 최신 1개만 가져옴
            List<BackData> latestDataList = backDataRepository.findLatestData(pageable, coin.name());

            if (latestDataList != null && !latestDataList.isEmpty()) {
                BackData latestData = latestDataList.get(0);  // 첫 번째 데이터 가져오기
                LocalDate latestDay = LocalDate.parse(latestData.getDay(), DateTimeFormatter.ISO_DATE);
                int daysBetween = (int) ChronoUnit.DAYS.between(latestDay, today) + 1;
                getData(String.valueOf(daysBetween));
            } else {
                log.warn("====== 데이터가 없습니다 초기 데이터를 업데이트합니다 ======");
                getData("200");
            }
            backDataMap.put(coin, backDataRepository.findByCoinAndTradingStatusOrderByDayDesc(coin.name(), "O"));
        }
    }

    @Transactional
    public void getData(String day) {
        for (Coin coin : Coin.values()) {
            String url = "https://api.upbit.com/v1/candles/days?market=KRW-" + coin + "&count=" + day;
            try {
                // API 호출
                String jsonResponse = fetchApiData(url);

                // JSON 파싱
                UpbitCandle[] candles = parseCandleData(jsonResponse);

                // 시간 계산
                int hour = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).getHour();
                int j = hour < 9 ? 2 : 1;
                for (int i = j; i < candles.length - 1; i++) {
                    String days = candles[i].getCandleDateTimeKst().substring(0, 10);
                    double targetPrice = candles[i + 1].getTradePrice() + (candles[i + 1].getHighPrice() - candles[i + 1].getLowPrice()) * 0.5;
                    double todayHighPrice = candles[i].getHighPrice();
                    String tradingStatus = todayHighPrice >= targetPrice ? "O" : "X";
                    double todayTradePrice = candles[i].getTradePrice();
                    double returnRate = (todayTradePrice - targetPrice) / targetPrice * 100;
                    returnRate = Math.round(returnRate * 10.0) / 10.0;

                    BackData findCoin = backDataRepository.findByCoinAndDay(coin.name(), day).orElse(null);
                    // DB 및 캐시 저장
                    if (findCoin == null) { // 백데이터 중복 확인
                        BackData newBackData = saveBackData(days, coin, tradingStatus, returnRate);
                        if(backDataMap.containsKey(coin) && newBackData.getTradingStatus().equals("O")){
                            List<BackData> backDataList = backDataMap.get(coin);
                            backDataList.add(newBackData);
                            backDataList.sort(Comparator.comparing(BackData::getDay).reversed());
                        }
                        log.info("✅{}일 백데이터 추가 완료", days);
                    } else {
                        log.info("❌이미 해당 날짜의 백데이터가 존재합니다.");
                    }
                }
            } catch (IOException e) {
                log.error("API 호출 중 오류 발생: {}", e.getMessage(), e);
                throw new IllegalArgumentException("데이터를 가져오는 중 오류가 발생했습니다.", e);
            }
        }
    }

    private String fetchApiData(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("accept", "application/json")
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API 호출 실패: " + response);
            }
            assert response.body() != null;
            return response.body().string();
        }
    }

    private UpbitCandle[] parseCandleData(String jsonResponse) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonResponse, UpbitCandle[].class);
    }

    private BackData saveBackData(String day, Coin coin, String tradingStatus, double returnRate) {
        BackData backData = new BackData(day, coin.name(), tradingStatus, returnRate);
        backDataRepository.save(backData);
        return backData;
    }
}
