package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.dto.AccountResponse;
import coin.cointrading.dto.TestOrderResponse;
import coin.cointrading.util.JwtTokenProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class UpbitServiceTest {

    UpbitService upbitService;

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @Mock
    RestTemplate restTemplate;

    private final String serverUrl = "https://api.upbit.com";

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        upbitService = new UpbitServicePassThroughImpl(jwtTokenProvider, restTemplate);
    }

    @Test
    void getAccount_transform_success() throws Exception {
        // given
        AuthUser authUser1 = new AuthUser("user1", "nick1", "secret1", "access1");
        AuthUser authUser2 = new AuthUser("user2", "nick2", "secret2", "access2");
        String fakeToken1 = "fake.jwt.token1";
        String fakeToken2 = "fake.jwt.token2";
        String requestUrl = serverUrl + "/v1/accounts";
        when(jwtTokenProvider.createAccountToken(authUser1)).thenReturn(fakeToken1);
        when(jwtTokenProvider.createAccountToken(authUser2)).thenReturn(fakeToken2);

        List<Map<String, Object>> accounts1 = List.of(
                Map.of(
                        "currency", "KRW",
                        "balance", "1000000.0",
                        "locked", "0.0",
                        "avg_buy_price", "0",
                        "avg_buy_price_modified", false,
                        "unit_currency", "KRW"
                ),
                Map.of(
                        "currency", "BTC",
                        "balance", "2.0",
                        "locked", "0.0",
                        "avg_buy_price", "10000000",
                        "avg_buy_price_modified", false,
                        "unit_currency", "KRW"
                )
        );

        List<Map<String, Object>> accounts2 = List.of(
                Map.of(
                        "currency", "KRW",
                        "balance", "500000.0",
                        "locked", "0.0",
                        "avg_buy_price", "0",
                        "avg_buy_price_modified", false,
                        "unit_currency", "KRW"
                ),
                Map.of(
                        "currency", "ETH",
                        "balance", "1.0",
                        "locked", "0.0",
                        "avg_buy_price", "1000000",
                        "avg_buy_price_modified", false,
                        "unit_currency", "KRW"
                )
        );

        when(restTemplate.exchange(
                eq(requestUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(accounts1, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(accounts2, HttpStatus.OK));

        // when
        ObjectMapper objectMapper = new ObjectMapper();
        List<AccountResponse> result1 = objectMapper.convertValue(upbitService.getAccount(authUser1), new TypeReference<>() {
        });
        List<AccountResponse> result2 = objectMapper.convertValue(upbitService.getAccount(authUser2), new TypeReference<>() {
        });

        // then
        assertThat(result1.get(0).getCurrency()).isEqualTo(accounts1.get(0).get("currency")); // KRW
        assertThat(result1.get(0).getBalance()).isEqualTo(accounts1.get(0).get("balance")); // 1000000.0
        assertThat(result1.get(1).getCurrency()).isEqualTo(accounts1.get(1).get("currency")); // BTC
        assertThat(result1.get(1).getBalance()).isEqualTo(accounts1.get(1).get("balance")); // 2.0
        assertThat(result1.get(1).getAvgBuyPrice()).isEqualTo(accounts1.get(1).get("avg_buy_price")); // 10000000
        assertThat(result2.get(0).getCurrency()).isEqualTo(accounts2.get(0).get("currency")); // KRW
        assertThat(result2.get(0).getBalance()).isEqualTo(accounts2.get(0).get("balance")); // 500000.0
        assertThat(result2.get(1).getCurrency()).isEqualTo(accounts2.get(1).get("currency")); // ETH
        assertThat(result2.get(1).getBalance()).isEqualTo(accounts2.get(1).get("balance")); // 1.0
        assertThat(result2.get(1).getAvgBuyPrice()).isEqualTo(accounts2.get(1).get("avg_buy_price")); // 1000000
    }

    @Test
    void order_buy_transform_success() throws Exception {
        // given
        AuthUser authUser1 = new AuthUser("user1", "nick1", "secret1", "access1");
        String decision = "buy";
        String requestUrl = serverUrl + "/v1/orders";

        // 계좌 정보 mock 설정
        List<Map<String, Object>> accounts1 = List.of(
                Map.of(
                        "currency", "KRW",
                        "balance", "1000000.0",
                        "locked", "0.0",
                        "avg_buy_price", "0",
                        "avg_buy_price_modified", false,
                        "unit_currency", "KRW"
                ),
                Map.of(
                        "currency", "BTC",
                        "balance", "2.0",
                        "locked", "0.0",
                        "avg_buy_price", "10000000",
                        "avg_buy_price_modified", false,
                        "unit_currency", "KRW"
                )
        );

        when(restTemplate.exchange(
                eq(serverUrl + "/v1/accounts"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(accounts1, HttpStatus.OK));
        when(upbitService.getAccount(authUser1)).thenReturn(new ResponseEntity<>(accounts1, HttpStatus.OK));

        double ETH_Price = 1000000;
        double price = (Double.parseDouble(accounts1.get(0).get("balance").toString()) * 0.9995);
        double volume = price / ETH_Price;
        // 주문 mock 데이터
        Map<String, Object> order1 = Map.ofEntries(
                Map.entry("uuid", "cdd92199-2897-4e14-9448-f923320408ad"),
                Map.entry("side", "bid"),
                Map.entry("ord_type", "limit"),
                Map.entry("price", Double.toString(price)),
                Map.entry("state", "wait"),
                Map.entry("market", "KRW-ETH"),
                Map.entry("created_at", "2018-04-10T15:42:23+09:00"),
                Map.entry("volume", Double.toString(volume)),
                Map.entry("remaining_volume", "0.01"),
                Map.entry("reserved_fee", "0.0005"),
                Map.entry("remaining_fee", "0.0005"),
                Map.entry("paid_fee", "0.0"),
                Map.entry("locked", "0"),
                Map.entry("executed_volume", "0.0"),
                Map.entry("trades_count", 0)
        );

        when(restTemplate.exchange(
                eq(requestUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(order1, HttpStatus.OK));

        // when
        ObjectMapper objectMapper = new ObjectMapper();
        TestOrderResponse result1 = objectMapper.convertValue(upbitService.orderCoins(decision, authUser1), new TypeReference<>() {});

        // then
        assertThat(result1.getMarket()).isEqualTo(order1.get("market")); // KRW-ETH
        assertThat(result1.getVolume()).isEqualTo(order1.get("volume")); // 0.09995
        assertThat(result1.getSide()).isEqualTo(order1.get("side")); // bid
    }

    @Test
    void order_sell_transform_success() throws Exception {
        // given
        AuthUser authUser1 = new AuthUser("user1", "nick1", "secret1", "access1");
        String decision = "sell";
        String requestUrl = serverUrl + "/v1/orders";

        // 계좌 정보 mock 설정
        List<Map<String, Object>> accounts1 = List.of(
                Map.of(
                        "currency", "KRW",
                        "balance", "1000000.0",
                        "locked", "0.0",
                        "avg_buy_price", "0",
                        "avg_buy_price_modified", false,
                        "unit_currency", "KRW"
                ),
                Map.of(
                        "currency", "ETH",
                        "balance", "1.15",
                        "locked", "0.0",
                        "avg_buy_price", "1000000",
                        "avg_buy_price_modified", false,
                        "unit_currency", "KRW"
                )
        );

        when(restTemplate.exchange(
                eq(serverUrl + "/v1/accounts"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(accounts1, HttpStatus.OK));
        when(upbitService.getAccount(authUser1)).thenReturn(new ResponseEntity<>(accounts1, HttpStatus.OK));

        String ETH_Price = "1200000.0";
        String volume = accounts1.get(1).get("balance").toString();
        double price = (Double.parseDouble(accounts1.get(0).get("balance").toString()) * 0.9995);
        // 주문 mock 데이터
        Map<String, Object> order1 = Map.ofEntries(
                Map.entry("uuid", "cdd92199-2897-4e14-9448-f923320408ad"),
                Map.entry("side", "ask"),
                Map.entry("ord_type", "limit"),
                Map.entry("price", ETH_Price),
                Map.entry("state", "wait"),
                Map.entry("market", "KRW-ETH"),
                Map.entry("created_at", "2018-04-10T15:42:23+09:00"),
                Map.entry("volume", volume),
                Map.entry("remaining_volume", "0.01"),
                Map.entry("reserved_fee", "0.0005"),
                Map.entry("remaining_fee", "0.0005"),
                Map.entry("paid_fee", "0.0"),
                Map.entry("locked", "0"),
                Map.entry("executed_volume", "0.0"),
                Map.entry("trades_count", 0)
        );

        when(restTemplate.exchange(
                eq(requestUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(order1, HttpStatus.OK));

        // when
        ObjectMapper objectMapper = new ObjectMapper();
        TestOrderResponse result1 = objectMapper.convertValue(upbitService.orderCoins(decision, authUser1), new TypeReference<>() {});

        // then
        assertThat(result1.getMarket()).isEqualTo(order1.get("market")); // KRW-ETH
        assertThat(result1.getVolume()).isEqualTo(order1.get("volume")); // 1.15
        assertThat(result1.getSide()).isEqualTo(order1.get("side")); // ask
    }
}