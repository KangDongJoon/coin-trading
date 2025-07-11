package coin.cointrading.service.impl;

import coin.cointrading.domain.Coin;
import coin.cointrading.domain.User;
import coin.cointrading.dto.AccountResponse;
import coin.cointrading.dto.OrderResponse;
import coin.cointrading.service.UpbitService;
import coin.cointrading.util.JwtTokenProvider;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Primary
public class UpbitServiceImpl implements UpbitService {

    private final String serverUrl = "https://api.upbit.com";
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;

    @Override
    public List<AccountResponse> getAccount(User requestUser) throws Exception {
        String accountUrl = serverUrl + "/v1/accounts";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        headers.set("Authorization", jwtTokenProvider.createAccountToken(requestUser));
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
    public Object orderCoins(String decision, User requestUser, Coin selectCoin) throws Exception {

        List<AccountResponse> account = getAccount(requestUser);
        AccountResponse KRW = new AccountResponse();
        AccountResponse coinAccount = new AccountResponse();
        for (AccountResponse accountResponse : account) {
            if (accountResponse.getCurrency().equals("KRW")) KRW = accountResponse;
            if (accountResponse.getCurrency().equals(selectCoin.name())) coinAccount = accountResponse;
        }

        String side;
        if ("buy".equals(decision)) side = "bid";
        else if ("sell".equals(decision)) side = "ask";
        else return null;

        // 잔고 계산
        double balance = Math.floor(Double.parseDouble(KRW.getBalance()) * 0.9995);
        String price = Double.toString(balance);
        String volume = coinAccount.getBalance();
        String ord_type = side.equals("bid") ? "price" : "market";

        HashMap<String, String> params = new HashMap<>();
        params.put("market", selectCoin.getMarketCode());
        params.put("side", side);
        params.put("ord_type", ord_type);

        if (side.equals("bid")) params.put("price", price);
        else params.put("volume", volume);

        String orderUrl = serverUrl + "/v1/orders";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        headers.set("Authorization", jwtTokenProvider.createOrderToken(params, requestUser));
        HttpEntity<String> entity = new HttpEntity<>(new Gson().toJson(params), headers);

        return restTemplate.exchange(
                orderUrl,
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<OrderResponse>() {
                }).getBody();
    }

    @Override
    public Object getOrders(User requestUser, int count, Coin selectCoin) {
        HashMap<String, String> params = new HashMap<>();
        params.put("market", selectCoin.getMarketCode());

        String limit = "limit=" + count;
        String[] states = {
                "done",
                "cancel"
        };

        ArrayList<String> queryElements = new ArrayList<>();
        for (Map.Entry<String, String> entity : params.entrySet()) {
            queryElements.add(entity.getKey() + "=" + entity.getValue());
        }
        for (String state : states) {
            queryElements.add("states[]=" + state);
        }
        queryElements.add(limit);

        String queryString = String.join("&", queryElements.toArray(new String[0]));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            headers.set("Authorization", jwtTokenProvider.createGetOrderToken(queryString, requestUser));
            HttpEntity<?> entity = new HttpEntity<>(headers);

            return restTemplate.exchange(
                    serverUrl + "/v1/orders/closed?" + queryString,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    }
            ).getBody();
        } catch (Exception e) {
            log.info(e.getMessage());
            return null;
        }
    }

}
