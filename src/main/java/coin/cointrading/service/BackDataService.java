package coin.cointrading.service;

import coin.cointrading.domain.BackData;
import coin.cointrading.dto.UpbitCandle;
import coin.cointrading.repository.BackDataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BackDataService {

    private final BackDataRepository backDataRepository;
    private final OkHttpClient okHttpClient;

    @PostConstruct
    public void postBackData() {
        LocalDate today = LocalDate.now();
        Pageable pageable = PageRequest.of(0, 1); // 최신 1개만 가져옴
        List<BackData> latestDataList = backDataRepository.findLatestData(pageable);

        if (latestDataList != null && !latestDataList.isEmpty()) {
            BackData latestData = latestDataList.get(0);  // 첫 번째 데이터 가져오기
            LocalDate latestDay = LocalDate.parse(latestData.getDay(), DateTimeFormatter.ISO_DATE);
            int daysBetween = (int) ChronoUnit.DAYS.between(latestDay, today);
            getData(String.valueOf(daysBetween));
        } else {
            log.warn("데이터가 없습니다. 기초 데이터를 업데이트합니다.");
            getData("200");
        }
    }

    public List<BackData> getBackData() {
        return backDataRepository.findAllActiveTrading();
    }

    // 7일, 30일, 100일 백데이터 조회 메소드 추가 예시
    public List<BackData> get7BackData() {
        return backDataRepository.findAllActiveTrading();
    }

    public List<BackData> get30BackData() {
        return backDataRepository.findAllActiveTrading();
    }

    public List<BackData> get100BackData() {
        return backDataRepository.findAllActiveTrading();
    }

    @Transactional
    public void getData(String day) {
        String url = "https://api.upbit.com/v1/candles/days?market=KRW-ETH&count=" + day;
        try {
            // API 호출
            String jsonResponse = fetchApiData(url);

            // JSON 파싱
            UpbitCandle[] candles = parseCandleData(jsonResponse);

            // 시간 계산
            int hour = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).getHour();
            int j = hour >= 0 && hour < 9 ? 2 : 1;
            for (int i = j; i < candles.length; i++) {
                String days = candles[i].getCandleDateTimeKst().substring(0, 10);
                double targetPrice = candles[i].getTradePrice() + (candles[i].getHighPrice() - candles[i].getLowPrice()) * 0.5;
                double todayHighPrice = candles[i].getHighPrice();
                String tradingStatus = todayHighPrice >= targetPrice ? "O" : "X";
                double returnRate = Math.round((((candles[i].getTradePrice() - targetPrice) / targetPrice) * 100) * 10.0) / 10.0;

                System.out.println(days);
                // DB 저장
                saveBackData(days, tradingStatus, returnRate);
            }
        } catch (IOException e) {
            log.error("API 호출 중 오류 발생: {}", e.getMessage(), e);
            throw new IllegalArgumentException("데이터를 가져오는 중 오류가 발생했습니다.", e);
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
            return response.body().string();
        }
    }

    private UpbitCandle[] parseCandleData(String jsonResponse) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonResponse, UpbitCandle[].class);
    }

    private void saveBackData(String day, String tradingStatus, double returnRate) {
        BackData backData = new BackData(day, tradingStatus, returnRate);
        backDataRepository.save(backData);
    }
}
