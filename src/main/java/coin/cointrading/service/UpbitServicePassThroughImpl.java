package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.util.JwtTokenProvider;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Qualifier("passthrough")
public class UpbitServicePassThroughImpl implements UpbitService {

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
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }
        ).getBody();
    }

    @Override
    public Object orderCoins(String decision, AuthUser authUser) throws Exception {
        List<Map<String, Object>> account = (List<Map<String, Object>>) getAccount(authUser);

        Map<String, Object> KRW = new HashMap<>();
        Map<String, Object> ETH = new HashMap<>();

        for (Map<String, Object> accountResponse : account) {
            String currency = (String) accountResponse.get("currency");
            if ("KRW".equals(currency)) KRW = accountResponse;
            else if ("ETH".equals(currency)) ETH = accountResponse;
        }

        String side;
        if ("buy".equals(decision)) side = "bid";
        else if ("sell".equals(decision)) side = "ask";
        else return null;

        // 잔고 계산
        double balance = Math.floor(Double.parseDouble((String) KRW.get("balance")) * 0.9995);
        String price = Double.toString(balance);
        String volume = (String) ETH.get("balance");
        String ord_type = "bid".equals(side) ? "price" : "market";

        // 주문 파라미터 설정
        HashMap<String, String> params = new HashMap<>();
        params.put("market", "KRW-ETH");
        params.put("side", side);
        params.put("ord_type", ord_type);

        if ("bid".equals(side)) params.put("price", price);
        else params.put("volume", volume);

        // 주문 요청
        String orderUrl = serverUrl + "/v1/orders";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("Authorization", jwtTokenProvider.createOrderToken(params, authUser));
        HttpEntity<String> entity = new HttpEntity<>(new Gson().toJson(params), headers);

        // 주문 응답 반환
        return restTemplate.exchange(
                orderUrl,
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
    }

    @Override
    public Object getOrders(AuthUser authUser, int count) {
        return null;
    }
}
