package coin.cointrading.controller;

import coin.cointrading.dto.AccountResponse;
import coin.cointrading.service.TradingService;
import coin.cointrading.util.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TradingController {

    private final TradingService tradingService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;
    private final String serverUrl = "https://api.upbit.com";

    @GetMapping("/v1/account")
    @Operation(summary = "계좌조회", description = "내 계좌 조회")
    public List<AccountResponse> getaccount() {
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


