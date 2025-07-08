package coin.cointrading.controller;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.dto.TradingStatus;
import coin.cointrading.service.TradingService;
import coin.cointrading.service.UpbitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TradingTestController {

    private final TradingService tradingService;
    private final UpbitService upbitService;
    private final ConcurrentHashMap<String, TradingStatus> userStatusMap;


    // test API
    @PostMapping("/test/async")
    public void testAsync(@AuthenticationPrincipal AuthUser authUser) throws InterruptedException {
        tradingService.asyncTest(authUser);
        log.info("비동기 매수매도 실행-Controller");
    }

    @GetMapping("/test/op-change")
    public void opChange() {
        log.info("-----실행중인 유저 op_mode 변경-----");
        tradingService.opChange();
        log.info("-----op_mode 변경완료-----");
    }

    @GetMapping("/test/buy-change")
    public void holdChange() {
        log.info("-----실행중인 유저 buy_status 변경-----");
        tradingService.holdChange();
        log.info("-----hold 변경완료-----");
    }

    @PostMapping("/v1/test/orders/sell")
    public ResponseEntity<Object> order(@AuthenticationPrincipal AuthUser authUser) throws Exception {
        String decision = "sell";
        return ResponseEntity.ok(upbitService.orderCoins(decision, authUser, userStatusMap.get(authUser.getUserId()).getSelectCoin()));
    }
}
