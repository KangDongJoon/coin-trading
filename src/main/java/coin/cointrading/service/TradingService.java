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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
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

@Service
@RequiredArgsConstructor
public class TradingService {

    @Value("${openai.api.key}")
    private String openAiKey;
    private final String serverUrl = "https://api.upbit.com";
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;

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
        double todayTarget = checkTarget(); // 당일 목표가
        boolean op_mode = false; // 시작하는 날 매수하지않기
        boolean hold = false; // 이미 매수한 상태라면 매수시도 하지않기(true = 매수, false = 매도)

        while (true) {
            LocalTime now = LocalTime.now(); // 현재시간
            double current = current(); // 현재가
            // 목표가 지정 9시 00분 20 ~ 30초 사이
            if (now.getHour() == 9 && now.getMinute() == 0 && (20 < now.getSecond() && 30 > now.getSecond())) {
                todayTarget = checkTarget(); // 목표가 갱신
                op_mode = true;
            }

            // 매수 로직
            if(op_mode && !hold && current >= todayTarget){
                // 매수 api
                orderCoins("buy");
                hold = true;
            }
            // 매도 로직
            if(op_mode && hold && now.getHour() == 8 && now.getMinute() == 59 && (50 <= now.getSecond() && 59 >= now.getSecond())){
                // 매도 api
                orderCoins("sell");
                hold = false;
                op_mode = false;
                Thread.sleep(10000);
            }

            Thread.sleep(1000);

//            System.out.printf("현재시간: %s 목표가: %s 현재가: %s 보유상태: %s 동작상태: %s%n",
//                    now, todayTarget, current, hold, op_mode);
        }
    }

    private OrderResponse orderCoins(String decision) throws IOException, NoSuchAlgorithmException {
        List<AccountResponse> account = getAccount();

        AccountResponse KRW = new AccountResponse();
        AccountResponse ETH = new AccountResponse();

        for (AccountResponse accountResponse : account) {
            if (accountResponse.getCurrency().equals("KRW")) KRW = accountResponse;
            else if (accountResponse.getCurrency().equals("ETH")) ETH = accountResponse;
        }

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

//    public OrderResponse orderCoin() throws IOException, NoSuchAlgorithmException {
//        List<AccountResponse> account = getAccount();
//
//        AccountResponse KRW = new AccountResponse();
//        AccountResponse ETH = new AccountResponse();
//
//        for (AccountResponse accountResponse : account) {
//            if (accountResponse.getCurrency().equals("KRW")) KRW = accountResponse;
//            else if (accountResponse.getCurrency().equals("ETH")) ETH = accountResponse;
//        }
//
//        ObjectMapper objectMapper = new ObjectMapper();
//        JsonNode jsonNode = objectMapper.readTree(aiDecision());
//
//        String decision = jsonNode.path("decision").asText();
//        String side;
//        if (decision.equals("buy")) {
//            side = "bid";
//        } else if (decision.equals("sell")) {
//            side = "ask";
//        } else {
//            return null;
//        }
//
//        double balance = Math.floor(Double.parseDouble(KRW.getBalance()) * 0.9995);
//        String price = Double.toString(balance);
//        String volume = ETH.getBalance();
//        String ord_type = side.equals("bid") ? "price" : "market";
//
//        HashMap<String, String> params = new HashMap<>();
//        params.put("market", "KRW-ETH");
//        params.put("side", side);
//        params.put("ord_type", ord_type);
//
//        if (side.equals("bid")) {
//            params.put("price", price);
//        } else {
//            params.put("volume", volume);
//        }
//
//        String orderUrl = serverUrl + "/v1/orders";
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Content-Type", "application/json");
//        headers.set("Authorization", jwtTokenProvider.createOrderToken(params));
//        HttpEntity<String> entity = new HttpEntity<>(new Gson().toJson(params), headers);
//
//        return restTemplate.exchange(
//                orderUrl,
//                HttpMethod.POST,
//                entity,
//                new ParameterizedTypeReference<OrderResponse>() {
//                }).getBody();
//    }
//
//    public String aiDecision() throws IOException {
//        final String gptUrl = "https://api.openai.com/v1/chat/completions";
//
//        OkHttpClient client = new OkHttpClient();
//        ObjectMapper objectMapper = new ObjectMapper();
//
//        String extractedData = candleExtract();  // 이 데이터는 JSON 배열 형태입니다.
//
//        // extractedData를 안전하게 JSON으로 포맷
//        ObjectNode requestBodyNode = objectMapper.createObjectNode();
//        requestBodyNode.put("model", "gpt-3.5-turbo");
//        requestBodyNode.put("temperature", 1);
//        requestBodyNode.put("max_tokens", 2048);
//        requestBodyNode.put("top_p", 1);
//        requestBodyNode.put("frequency_penalty", 0);
//        requestBodyNode.put("presence_penalty", 0);
//
//        ArrayNode messages = requestBodyNode.putArray("messages");
//        ObjectNode systemMessage = messages.addObject();
//        systemMessage.put("role", "system");
//        systemMessage.put("content", "You're a Coin Invest expert. Tell me if I should buy, sell, or hold based on the chart data provided. Respond in JSON format. Answer in Korean.\n\nResponse Example:\n{\"decision\": \"buy\", \"reason\": \"기술적인 이유 설명\"}\n{\"decision\": \"sell\", \"reason\": \"기술적인 이유 설명\"}\n{\"decision\": \"hold\", \"reason\": \"기술적인 이유 설명\"}");
//
//
//        ObjectNode userMessage = messages.addObject();
//        userMessage.put("role", "user");
//        userMessage.put("content", extractedData);  // 그대로 이 데이터를 JSON 형식으로 삽입
//
//        // HTTP 요청 생성
//        Request request = new Request.Builder()
//                .url(gptUrl)
//                .addHeader("Authorization", "Bearer " + openAiKey)
//                .addHeader("Content-Type", "application/json")
//                .post(RequestBody.create(requestBodyNode.toString(), MediaType.get("application/json")))
//                .build();
//
//        // 요청 실행 및 응답 처리
//        try (Response response = client.newCall(request).execute()) {
//            if (!response.isSuccessful()) {
//                throw new IOException("Unexpected response code: " + response.code() + " " + response.body().string());
//            }
//
//            // JSON 응답 파싱
//            JsonNode jsonResponse = objectMapper.readTree(response.body().string());
//
//            // 필요한 데이터 추출
//            JsonNode choices = jsonResponse.path("choices");
//            if (choices.isArray() && !choices.isEmpty()) {
//                JsonNode decision = choices.get(0).path("message").path("content");
//                return decision.asText();
//            } else {
//                throw new IOException("No choices in the response.");
//            }
//        }
//    }
//
//    @NotNull
//    private static String candleExtract() throws IOException {
//        // 업비트 차트 전달 15일치 일봉데이터 가져오기
//        OkHttpClient client = new OkHttpClient();
//
//        Request request = new Request.Builder()
//                .url("https://api.upbit.com/v1/candles/days?market=KRW-ETH&count=15")
//                .get()
//                .addHeader("accept", "application/json")
//                .build();
//
//        // try-with-resources 구문을 사용하여 자동으로 닫히도록 함
//        try (Response response = client.newCall(request).execute()) {
//            String jsonResponse = response.body().string();
//
//            // Jackson을 이용한 JSON 파싱
//            ObjectMapper objectMapper = new ObjectMapper();
//            UpbitCandle[] candles = objectMapper.readValue(jsonResponse, UpbitCandle[].class);
//            SimpleCandleDTO[] filteredCandles = Arrays.stream(candles)
//                    .map(candle -> new SimpleCandleDTO(candle.getCandleDateTimeKst(), candle.getOpeningPrice(), candle.getHighPrice(), candle.getLowPrice(), candle.getTradePrice(), candle.getCandleAccTradeVolume()))
//                    .toArray(SimpleCandleDTO[]::new);
//
//            return objectMapper.writeValueAsString(filteredCandles);
//        }
//    }
}
