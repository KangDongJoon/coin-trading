package coin.cointrading.service.impl;

import coin.cointrading.dto.SimpleCandleDTO;
import coin.cointrading.dto.UpbitCandle;
import coin.cointrading.service.UpbitCandleService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
@Primary
public class UpbitCandleServiceImpl implements UpbitCandleService {

    private final String serverUrl = "https://api.upbit.com";

    @Override
    public String dayCandle() throws IOException {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://api.upbit.com/v1/candles/days?market=KRW-ETH&count=2")
                .get()
                .addHeader("accept", "application/json")
                .build();

        // try-with-resources 구문을 사용하여 자동으로 닫히도록 함
        try (Response response = client.newCall(request).execute()) {
            String jsonResponse = response.body().string();

            // Jackson을 이용한 JSON 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            UpbitCandle[] candles = objectMapper.readValue(jsonResponse, UpbitCandle[].class);
            SimpleCandleDTO[] filteredCandles = Arrays.stream(candles)
                    .map(candle -> new SimpleCandleDTO(candle.getCandleDateTimeKst(), candle.getOpeningPrice(), candle.getHighPrice(), candle.getLowPrice(), candle.getTradePrice(), candle.getCandleAccTradeVolume()))
                    .toArray(SimpleCandleDTO[]::new);

            return objectMapper.writeValueAsString(filteredCandles);
        }
    }

    @Override
    public Double current() throws IOException {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(serverUrl + "/v1/ticker?markets=KRW-ETH")
                .get()
                .addHeader("accept", "application/json")
                .build();

        // try-with-resources를 사용하여 자동으로 response 닫기
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            String jsonResponse = response.body().string();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);

            // trade_price(현재 가격) 값 추출
            return jsonNode.get(0).get("trade_price").asDouble();
        }
    }

    @Override
    public Double checkTarget() throws IOException {
        String candle = dayCandle();
        ObjectMapper objectMapper = new ObjectMapper();

        List<JsonNode> candles = objectMapper.readValue(candle, new TypeReference<>() {
        });
        JsonNode yesterday = candles.get(1);
        double lowPrice = yesterday.get("low_price").asDouble();
        double highPrice = yesterday.get("high_price").asDouble();
        double targetPoint = (highPrice - lowPrice) * 0.5;
        double targetPrice = yesterday.get("trade_price").asDouble() + targetPoint;

        return targetPrice;
    }
}
