package coin.cointrading.service;

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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingService {

    private final String serverUrl = "https://api.upbit.com";
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;
    private boolean running;

    public List<AccountResponse> getAccount() {
        String accountUrl = serverUrl + "/v1/accounts";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("Authorization", jwtTokenProvider.createAccountToken());
        HttpEntity<?> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(
                accountUrl,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<AccountResponse>>() {
                }).getBody();
    }

    public void startProgram() throws IOException, InterruptedException, NoSuchAlgorithmException {
        log.info("프로그램 동작 시작");
        running = true;
        double todayTarget = checkTarget(); // 당일 목표가
        boolean op_mode = false; // 시작하는 날 매수하지않기
        boolean hold = false; // 이미 매수한 상태라면 매수시도 하지않기(true = 매수, false = 매도)
        boolean stopLossExecuted = false; // 손절 여부 추적

        LocalTime alarmTime = LocalTime.now().plusMinutes(30);
        while (running) {
            LocalTime now = LocalTime.now(); // 현재시간
            double current = current(); // 현재가
            // 목표가 지정 9시 00분 20 ~ 30초 사이
            if (now.getHour() == 9 && now.getMinute() == 0 && (20 < now.getSecond() && 30 > now.getSecond())) {
                try {
                    todayTarget = checkTarget(); // 목표가 갱신
                    log.info("목표가 갱신: {}", todayTarget);
                    op_mode = true;
                    stopLossExecuted = false;
                } catch (Exception e) {
                    // 예외가 발생하면 로그에 기록
                    log.error("목표가 산정 중 오류 발생: {}", e.getMessage());
                    e.printStackTrace();
                }
            }

            // 매수 로직
            if (op_mode && !hold && current >= todayTarget && !stopLossExecuted) {
                // 매수 api
                OrderResponse orderResponse = orderCoins("buy");
                hold = true;
                log.info("매수 수량: {}", orderResponse.getVolume());
            }

            // 매도 로직
            if (op_mode && hold && now.getHour() == 8 && now.getMinute() == 59 && (now.getSecond() >= 50 && now.getSecond() <= 59)) {
                try {
                    // 매도 api
                    OrderResponse orderResponse = orderCoins("sell");
                    hold = false;
                    op_mode = false;
                    double ror = ((Integer.parseInt(orderResponse.getPrice()) - todayTarget) / todayTarget) * 100;
                    ror = Math.round(ror * 10.0) / 10.0; // 소수점 한 자리까지 반올림
                    log.info("매도 수익률: {}%", ror);
                    Thread.sleep(10000);
                } catch (Exception e) {
                    // 예외가 발생하면 로그에 기록
                    log.error("매도 처리 중 오류 발생: {}", e.getMessage());
                    e.printStackTrace();
                }
            }

            // 손절 5%
            if (op_mode && hold && current < todayTarget * 0.95) {
                // 매도 api
                orderCoins("sell");
                log.info("손절 발생");
                hold = false;
                op_mode = false;
                stopLossExecuted = true;
            }

            if(now.getMinute() == alarmTime.getMinute()){
                log.info("---프로그램 실행 중--- 목표가: {}   매수여부: {}  ", todayTarget, hold);
                alarmTime = now.plusMinutes(30);
            }

            Thread.sleep(1000); // 특정시간마다 하게
        }
    }

    public void stopProgram() {
        running = false;
        log.info("프로그램 종료");
    }

    private OrderResponse orderCoins(String decision) throws IOException, NoSuchAlgorithmException {
        List<AccountResponse> account = getAccount();

        AccountResponse KRW = new AccountResponse();
        AccountResponse ETH = new AccountResponse();

        for (AccountResponse accountResponse : account) {
            if (accountResponse.getCurrency().equals("KRW")) KRW = accountResponse;
            else if (accountResponse.getCurrency().equals("ETH")) ETH = accountResponse;
        }

        // enum
        String side;
        if (decision.equals("buy")) {
            side = "bid";
        } else if (decision.equals("sell")) {
            side = "ask";
        } else {
            return null;
        }

        double balance = Math.floor(Double.parseDouble(KRW.getBalance()) * 0.9995);
        String price = Double.toString(balance);
        String volume = ETH.getBalance();
        String ord_type = side.equals("bid") ? "price" : "market";

        HashMap<String, String> params = new HashMap<>();
        params.put("market", "KRW-ETH");
        params.put("side", side);
        params.put("ord_type", ord_type);

        if (side.equals("bid")) {
            params.put("price", price);
        } else {
            params.put("volume", volume);
        }

        String orderUrl = serverUrl + "/v1/orders";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("Authorization", jwtTokenProvider.createOrderToken(params));
        HttpEntity<String> entity = new HttpEntity<>(new Gson().toJson(params), headers);

        return restTemplate.exchange(
                orderUrl,
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<OrderResponse>() {
                }).getBody();
    }

    private Double checkTarget() throws IOException {
        String candle = yesterdayCandle();
        ObjectMapper objectMapper = new ObjectMapper();

        List<JsonNode> candles = objectMapper.readValue(candle, new TypeReference<List<JsonNode>>() {
        });
        JsonNode yesterday = candles.get(1);
        double lowPrice = yesterday.get("low_price").asDouble();
        double highPrice = yesterday.get("high_price").asDouble();
        double targetPoint = (highPrice - lowPrice) * 0.5;
        double targetPrice = yesterday.get("trade_price").asDouble() + targetPoint;

        return targetPrice;
    }

    private String yesterdayCandle() throws IOException {
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

    private Double current() throws IOException {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(serverUrl + "/v1/ticker?markets=KRW-ETH")
                .get()
                .addHeader("accept", "application/json")
                .build();

        // try-with-resources를 사용하여 자동으로 response 닫기
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String jsonResponse = response.body().string();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);

            // trade_price(현재 가격) 값 추출
            return jsonNode.get(0).get("trade_price").asDouble();
        }
    }
}
