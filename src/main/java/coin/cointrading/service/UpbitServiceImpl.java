package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.dto.AccountResponse;
import coin.cointrading.dto.OrderResponse;
import coin.cointrading.dto.SimpleCandleDTO;
import coin.cointrading.dto.UpbitCandle;
import coin.cointrading.util.JwtTokenProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Primary
public class UpbitServiceImpl implements UpbitService {

    private final String serverUrl = "https://api.upbit.com";
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;

    @Override
    public Object getAccount(AuthUser authUser) throws Exception {
        String accountUrl = serverUrl + "/v1/accounts";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("Authorization", jwtTokenProvider.createAccountToken(authUser));
        HttpEntity<?> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(
                accountUrl,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<AccountResponse>>() {
                }
        ).getBody();
    }

    @Override
    public Object orderCoins(String decision, AuthUser authUser) throws Exception {

        @SuppressWarnings("unchecked")
        List<AccountResponse> account = (List<AccountResponse>) getAccount(authUser);
        AccountResponse KRW = new AccountResponse();
        AccountResponse ETH = new AccountResponse();

        for (AccountResponse accountResponse : account) {
            if (accountResponse.getCurrency().equals("KRW")) KRW = accountResponse;
            else if (accountResponse.getCurrency().equals("ETH")) ETH = accountResponse;
        }

        String side;
        if ("buy".equals(decision)) side = "bid";
        else if ("sell".equals(decision)) side = "ask";
        else return null;

        // 잔고 계산
        double balance = Math.floor(Double.parseDouble(KRW.getBalance()) * 0.9995);
        String price = Double.toString(balance);
        String volume = ETH.getBalance();
        String ord_type = side.equals("bid") ? "price" : "market";

        HashMap<String, String> params = new HashMap<>();
        params.put("market", "KRW-ETH");
        params.put("side", side);
        params.put("ord_type", ord_type);

        if (side.equals("bid")) params.put("price", price);
        else params.put("volume", volume);

        String orderUrl = serverUrl + "/v1/orders";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("Authorization", jwtTokenProvider.createOrderToken(params, authUser));
        HttpEntity<String> entity = new HttpEntity<>(new Gson().toJson(params), headers);

        return restTemplate.exchange(
                orderUrl,
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<OrderResponse>() {
                }).getBody();
    }


    public String yesterdayCandle() throws IOException {
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

    public Double checkTarget() throws IOException {
        String candle = yesterdayCandle();
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
