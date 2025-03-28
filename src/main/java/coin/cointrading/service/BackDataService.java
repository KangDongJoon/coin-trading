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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BackDataService {

    private final BackDataRepository backDataRepository;

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
        OkHttpClient client = new OkHttpClient();

        String url = "https://api.upbit.com/v1/candles/days?market=KRW-ETH&count=" + day;
        // 1=29, 2=29,28, 00~09시사이면
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("accept", "application/json")
                .build();

        // try-with-resources 구문을 사용하여 자동으로 닫히도록 함
        try (Response response = client.newCall(request).execute()) {
            String jsonResponse = response.body().string();

            // Jackson을 이용한 JSON 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            UpbitCandle[] candles = objectMapper.readValue(jsonResponse, UpbitCandle[].class);

            int hour = LocalTime.now().getHour();
            int j = hour >= 0 && hour < 9 ? 1 : 0;
            for (int i = candles.length - 2; i >= j; i--) {
                String days = candles[i].getCandleDateTimeKst().substring(0, 10); // 2025-02-16
                double targetPrice = candles[i + 1].getTradePrice() + (candles[i + 1].getHighPrice() - candles[i + 1].getLowPrice()) * 0.5;
                double todayHighPrice = candles[i].getHighPrice();
                String tradingStatus = todayHighPrice >= targetPrice ? "O" : "X";
                double returnRate = Math.round((((candles[i].getTradePrice() - targetPrice) / targetPrice) * 100) * 10.0) / 10.0;

                BackData backData = new BackData(
                        days,
                        tradingStatus,
                        returnRate);

                backDataRepository.save(backData);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("오류 발생: " + e.getMessage(), e);  // IOException을 IllegalArgumentException으로 감싸서 던짐
        }
    }
}
