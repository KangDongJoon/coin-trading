package coin.cointrading.service;

import coin.cointrading.dto.AccountResponse;
import coin.cointrading.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

}
