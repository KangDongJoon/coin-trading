package coin.cointrading.service;

import coin.cointrading.domain.BackData;
import coin.cointrading.dto.UpbitCandle;
import coin.cointrading.repository.BackDataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BackDataService {

    private final BackDataRepository backDataRepository;

    @Transactional
    public void postBackData() throws IOException {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://api.upbit.com/v1/candles/days?market=KRW-ETH&count=3")
                .get()
                .addHeader("accept", "application/json")
                .build();

        // try-with-resources 구문을 사용하여 자동으로 닫히도록 함
        try (Response response = client.newCall(request).execute()) {
            String jsonResponse = response.body().string();

            // Jackson을 이용한 JSON 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            UpbitCandle[] candles = objectMapper.readValue(jsonResponse, UpbitCandle[].class);

            for (int i = 1; i < candles.length - 1; i++) {
                String day = candles[i].getCandleDateTimeKst().substring(0, 10);
                double targetPrice = candles[i + 1].getTradePrice() + (candles[i + 1].getHighPrice() - candles[i + 1].getLowPrice()) * 0.5;
                double todayHighPrice = candles[i].getHighPrice();
                String tradingStatus = todayHighPrice >= targetPrice ? "O" : "X";
                double returnRate = Math.round((((candles[i].getTradePrice() - targetPrice) / targetPrice) * 100) * 10.0) / 10.0;

                BackData backData = new BackData(
                        day,
                        tradingStatus,
                        returnRate);

                backDataRepository.save(backData);
            }
        }
    }

    public List<BackData> getBackData() {
        return backDataRepository.findAllActiveTrading();
    }

    @Transactional
    public void initBackData() throws IOException {
        if (backDataRepository.findAll().isEmpty()) {
            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url("https://api.upbit.com/v1/candles/days?market=KRW-ETH&count=200")
                    .get()
                    .addHeader("accept", "application/json")
                    .build();

            // try-with-resources 구문을 사용하여 자동으로 닫히도록 함
            try (Response response = client.newCall(request).execute()) {
                String jsonResponse = response.body().string();

                // Jackson을 이용한 JSON 파싱
                ObjectMapper objectMapper = new ObjectMapper();
                UpbitCandle[] candles = objectMapper.readValue(jsonResponse, UpbitCandle[].class);

                for (int i = 1; i < candles.length - 1; i++) {
                    String day = candles[i].getCandleDateTimeKst().substring(0, 10); // 2025-02-16
                    double targetPrice = candles[i + 1].getTradePrice() + (candles[i + 1].getHighPrice() - candles[i + 1].getLowPrice()) * 0.5;
                    double todayHighPrice = candles[i].getHighPrice();
                    String tradingStatus = todayHighPrice >= targetPrice ? "O" : "X";
                    double returnRate = Math.round((((candles[i].getTradePrice() - targetPrice) / targetPrice) * 100) * 10.0) / 10.0;

                    BackData backData = new BackData(
                            day,
                            tradingStatus,
                            returnRate);

                    backDataRepository.save(backData);
                }
            }
        }else{
            log.info("이미 백데이터가 존재합니다.");
        }
    }
}
