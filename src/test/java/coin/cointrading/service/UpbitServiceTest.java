package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.dto.AccountResponse;
import coin.cointrading.util.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class UpbitServiceTest {


    @InjectMocks
    UpbitService upbitService;

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @Mock
    RestTemplate restTemplate;

    private final String serverUrl = "https://api.upbit.com";

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getAccount_success() throws Exception {
        // given
        AuthUser authUser1 = new AuthUser("user1", "nick1", "secret1", "access1");
        AuthUser authUser2 = new AuthUser("user2", "nick2", "secret2", "access2");
        String fakeToken1 = "fake.jwt.token1";
        String fakeToken2 = "fake.jwt.token2";
        String requestUrl = serverUrl + "/v1/accounts";
        when(jwtTokenProvider.createAccountToken(authUser1)).thenReturn(fakeToken1);
        when(jwtTokenProvider.createAccountToken(authUser2)).thenReturn(fakeToken2);
        List<AccountResponse> user1Account = List.of(
                new AccountResponse("KRW", "80000.0", "0.0", "0", false, "KRW"),
                new AccountResponse("ETH", "2.0", "0.0", "100000", false, "KRW")
        );
        List<AccountResponse> user2Account = List.of(
                new AccountResponse("KRW", "100000.0", "0.0", "0", false, "KRW"),
                new AccountResponse("BTC", "1.0", "0.0", "10000000", false, "KRW"),
                new AccountResponse("ETH", "2.0", "0.0", "100000", false, "KRW")
        );

        ResponseEntity<List<AccountResponse>> responseEntity1 = new ResponseEntity<>(user1Account, HttpStatus.OK);
        ResponseEntity<List<AccountResponse>> responseEntity2 = new ResponseEntity<>(user2Account, HttpStatus.OK);
        when(restTemplate.exchange(
                eq(requestUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class))
        ).thenReturn(responseEntity1)
                .thenReturn(responseEntity2);
        // when
        List<AccountResponse> result1 = upbitService.getAccount(authUser1);
        List<AccountResponse> result2 = upbitService.getAccount(authUser2);


        // then
        // user1 계좌 확인
        assertThat(result1.size()).isEqualTo(2);
        assertThat(result1.get(0).getCurrency()).isEqualTo("KRW");
        assertThat(result1.get(0).getBalance()).isEqualTo("80000.0");
        assertThat(result1.get(1).getCurrency()).isEqualTo("ETH");
        assertThat(result1.get(1).getBalance()).isEqualTo("2.0");
        assertThat(result1.get(1).getAvgBuyPrice()).isEqualTo("100000");
        // user2 계좌 확인
        assertThat(result2.size()).isEqualTo(3);
        assertThat(result2.get(0).getCurrency()).isEqualTo("KRW");
        assertThat(result2.get(0).getBalance()).isEqualTo("100000.0");
        assertThat(result2.get(1).getCurrency()).isEqualTo("BTC");
        assertThat(result2.get(1).getBalance()).isEqualTo("1.0");
        assertThat(result2.get(1).getAvgBuyPrice()).isEqualTo("10000000");
    }
}