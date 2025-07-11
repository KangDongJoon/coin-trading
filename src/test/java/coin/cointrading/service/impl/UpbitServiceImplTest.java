package coin.cointrading.service.impl;

import coin.cointrading.domain.User;
import coin.cointrading.dto.AccountResponse;
import coin.cointrading.dto.OrderResponse;
import coin.cointrading.util.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static coin.cointrading.domain.Coin.BTC;
import static coin.cointrading.domain.Coin.ETH;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpbitServiceImplTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    coin.cointrading.service.impl.UpbitServiceImpl upbitService;

    private User requestUser;
    private List<AccountResponse> accountList;
    private OrderResponse buyOrderResponse;
    private OrderResponse sellOrderResponse;

    @BeforeEach
    void setUp() {
        requestUser = new User("test1", "password", "nickName", "secretKey", "accessKey");

        accountList = new ArrayList<>();
        AccountResponse accountResponse_KRW = new AccountResponse("KRW", "1000000.0", "0.0", "0", false, "KRW");
        AccountResponse accountResponse_BTC = new AccountResponse("BTC", "2.0", "0.0", "101000", false, "KRW");
        accountList.add(accountResponse_KRW);
        accountList.add(accountResponse_BTC);

        double currentCoinPrice = 100.0;
        double volume = Double.parseDouble(accountResponse_KRW.getBalance()) / currentCoinPrice;
        double locked = (currentCoinPrice * volume) * (1 + 0.005);
        buyOrderResponse = new OrderResponse("KRW-BTC", "bid", String.valueOf(currentCoinPrice), null, String.valueOf(volume), String.valueOf(locked));
        sellOrderResponse = new OrderResponse("KRW-BTC", "ask", null, String.valueOf(volume), "0", String.valueOf(volume));
    }

    @Test
    void getAccount_success() throws Exception {
        // given
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<List<AccountResponse>>>any()
        )).thenReturn(new ResponseEntity<>(accountList, HttpStatus.OK));

        // when
        List<AccountResponse> result = upbitService.getAccount(requestUser);

        // then
        assertThat(result.get(0).getCurrency()).isEqualTo("KRW");
        assertThat(result.get(0).getBalance()).isEqualTo("1000000.0");
        assertThat(result.get(1).getCurrency()).isEqualTo("BTC");
        assertThat(result.get(1).getBalance()).isEqualTo("2.0");
    }

    @Test
    void orderCoins_success() throws Exception {
        // given
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<List<AccountResponse>>>any()
        )).thenReturn(new ResponseEntity<>(accountList, HttpStatus.OK));

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<OrderResponse>>any()
        )).thenReturn(new ResponseEntity<>(buyOrderResponse, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(sellOrderResponse, HttpStatus.OK));

        // when
        OrderResponse buyOrderResult = (OrderResponse) upbitService.orderCoins("buy", requestUser, BTC);
        OrderResponse sellOrderResult = (OrderResponse) upbitService.orderCoins("sell", requestUser, BTC);

        // then
        assertThat(buyOrderResult.getMarket()).isEqualTo("KRW-BTC");
        assertThat(buyOrderResult.getSide()).isEqualTo("bid");
        assertThat(buyOrderResult.getExecutedVolume()).isEqualTo("10000.0");

        assertThat(sellOrderResult.getMarket()).isEqualTo("KRW-BTC");
        assertThat(sellOrderResult.getVolume()).isEqualTo(buyOrderResult.getExecutedVolume());
    }

    @Test
    void getOrder_success() {
        // given
        List<Map<String, Object>> orders = new ArrayList<>();
        Map<String, Object> order1 = Map.ofEntries(
                entry("uuid", "ad2759ac-a3af-4c5b-a9d0-d9c21d23c41d"),
                entry("side", "bid"),
                entry("ord_type", "price"),
                entry("price", "7000"),
                entry("state", "wait"),
                entry("market", "KRW-ETH"),
                entry("created_at", "2025-02-26T23:32:20+09:00"),
                entry("reserved_fee", "3.5"),
                entry("remaining_fee", "3.5"),
                entry("paid_fee", "0"),
                entry("locked", "7003.5"),
                entry("executed_volume", "0"),
                entry("trades_count", 0)
        );

        Map<String, Object> order2 = Map.ofEntries(
                entry("uuid", "c2cab8d5-b0fc-403b-80c1-0abcd35d339d"),
                entry("side", "ask"),
                entry("ord_type", "market"),
                entry("state", "wait"),
                entry("market", "KRW-ETH"),
                entry("created_at", "2025-02-26T23:32:42+09:00"),
                entry("volume", "0.00200688"),
                entry("remaining_volume", "0.00200688"),
                entry("reserved_fee", "0"),
                entry("remaining_fee", "0"),
                entry("paid_fee", "0"),
                entry("locked", "0.00200688"),
                entry("executed_volume", "0"),
                entry("trades_count", 0)
        );
        orders.add(order1);
        orders.add(order2);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<List<Map<String, Object>>>>any()
        )).thenReturn(new ResponseEntity<>(orders, HttpStatus.OK));

        // when
        List<Map<String, Object>> resultList = (List<Map<String, Object>>) upbitService.getOrders(requestUser, 2, ETH);
        Map<String, Object> buyResult = resultList.get(0);
        Map<String, Object> sellResult = resultList.get(1);

        // then
        assertThat(buyResult.get("side")).isEqualTo("bid");
        assertThat(buyResult.get("market")).isEqualTo("KRW-ETH");
        assertThat(buyResult.get("price")).isEqualTo("7000");
        assertThat(buyResult.get("locked")).isEqualTo("7003.5");

        assertThat(sellResult.get("side")).isEqualTo("ask");
        assertThat(sellResult.get("market")).isEqualTo("KRW-ETH");
        assertThat(sellResult.get("volume")).isEqualTo("0.00200688");
        assertThat(sellResult.get("locked")).isEqualTo("0.00200688");
    }

}