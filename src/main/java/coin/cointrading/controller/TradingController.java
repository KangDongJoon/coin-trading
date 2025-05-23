package coin.cointrading.controller;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.domain.BackData;
import coin.cointrading.service.TradingService;
import coin.cointrading.service.UpbitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TradingController {

    private final TradingService tradingService;
    private final UpbitService upbitService;

    @GetMapping("/v1/accounts")
    public ResponseEntity<Object> getAccount(@AuthenticationPrincipal AuthUser authUser) throws Exception {
        return ResponseEntity.ok(upbitService.getAccount(authUser));
    }

    @PostMapping("/v1/starts")
    public String startProgram(@AuthenticationPrincipal AuthUser authUser) {
        try {
            tradingService.startTrading(authUser);
            return authUser.getUserId() + "의 매매 프로그램이 정상적으로 실행되었습니다.";
        } catch (Exception e) {
            return authUser.getUserId() + "의 매매 프로그램 실행 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    @PostMapping("/v1/stops")
    public String stopProgram(@AuthenticationPrincipal AuthUser authUser) {
        try {
            tradingService.stopTrading(authUser);
            return authUser.getUserId() + "의 매매 프로그램이 정상적으로 종료되었습니다.";
        } catch (Exception e) {
            return authUser.getUserId() + "의 매매 프로그램 종료 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    // 상태 확인 API
    @GetMapping("/v1/status")
    public ResponseEntity<Map<String, String>> checkStatus(@AuthenticationPrincipal AuthUser authUser) {
        String status = tradingService.checkStatus(authUser); // 사용자 상태 반환
        Map<String, String> response = new HashMap<>();
        response.put("isRunning", status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/v1/backdata")
    public List<BackData> getBackData() {
        return null;
    }

    @PostMapping("/v1/orders/sell")
    public ResponseEntity<Object> order(@AuthenticationPrincipal AuthUser authUser) throws Exception {
        String decision = "sell";
        return ResponseEntity.ok(upbitService.orderCoins(decision, authUser));
    }

    @GetMapping("/v1/orders/close")
    public ResponseEntity<Object> getOrders(@AuthenticationPrincipal AuthUser authUser, @RequestParam int count) {
        return ResponseEntity.ok(upbitService.getOrders(authUser, count));
    }

    @PostMapping("/test/async")
    public void testAsync() throws InterruptedException {
        tradingService.asyncTest();
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

}


