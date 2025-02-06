package coin.cointrading.service;

import coin.cointrading.dto.AccountResponse;
import coin.cointrading.dto.UpbitCandle;
import coin.cointrading.util.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradingService {

    private final String serverUrl = "https://api.upbit.com";
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;

    public List<AccountResponse> getAccount() {
        String accountUrl = serverUrl + "/v1/accounts";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("Authorization", jwtTokenProvider.createToken());
        HttpEntity<?> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(
                accountUrl,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<AccountResponse>>() {
                }).getBody();
    }

    public String aiDecision() throws IOException {
        String candle = candleExtract();

        return candle;


    }


    @NotNull
    private static String candleExtract() throws IOException {
        // 업비트 차트 전달 7일으로 결정 총 3주치 전달
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://api.upbit.com/v1/candles/weeks?market=KRW-ETH&count=3")
                .get()
                .addHeader("accept", "application/json")
                .build();

        Response response = client.newCall(request).execute();

        String jsonResponse = response.body().string();

        // Jackson을 이용한 JSON 파싱
        ObjectMapper objectMapper = new ObjectMapper();
        UpbitCandle[] candles = objectMapper.readValue(jsonResponse, UpbitCandle[].class);

        // 파싱된 데이터를 출력 또는 추가 처리
        StringBuilder result = new StringBuilder();
        for (UpbitCandle candle : candles) {
            result.append("Date (KST): " + candle.getCandleDateTimeKst() + "\n")
            .append("Open: " + candle.getOpeningPrice() + "\n")
            .append("High: " + candle.getHighPrice() + "\n")
            .append("Low: " + candle.getLowPrice() + "\n")
            .append("Close: " + candle.getTradePrice() + "\n")
            .append("First Day: " + candle.getFirstDayOfPeriod() + "\n")
            .append("------------------------------\n");
        }

        return result.toString();
    }
}
