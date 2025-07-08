package coin.cointrading.controller;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.domain.BackData;
import coin.cointrading.dto.SelectCoin;
import coin.cointrading.service.TradingService;
import coin.cointrading.service.UpbitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * 계좌 확인
     */
    @GetMapping("/v1/accounts")
    public ResponseEntity<Object> getAccount(@AuthenticationPrincipal AuthUser authUser) throws Exception {
        return ResponseEntity.ok(upbitService.getAccount(authUser));
    }

    /**
     * 프로그램 실행
     */
    @PostMapping("/v1/starts")
    public String startProgram(@AuthenticationPrincipal AuthUser authUser, @RequestBody SelectCoin selectCoin) {
        try {
            String strCoin = selectCoin.getCoin();
            tradingService.startTrading(authUser, strCoin);
            return authUser.getUserId() + "의 매매 프로그램이 정상적으로 실행되었습니다.";
        } catch (Exception e) {
            return authUser.getUserId() + "의 매매 프로그램 실행 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    /**
     * 프로그램 종료
     */
    @PostMapping("/v1/stops")
    public String stopProgram(@AuthenticationPrincipal AuthUser authUser) {
        try {
            tradingService.stopTrading(authUser);
            return authUser.getUserId() + "의 매매 프로그램이 정상적으로 종료되었습니다.";
        } catch (Exception e) {
            return authUser.getUserId() + "의 매매 프로그램 종료 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    /**
     * 동작 상태 확인
     */
    @GetMapping("/v1/status")
    public ResponseEntity<Map<String, String>> checkStatus(@AuthenticationPrincipal AuthUser authUser) {
        // 사용자 상태 반환
        Map<String, String> response = new HashMap<>();
        response.put("isRunning", tradingService.checkStatus(authUser));
        response.put("selectedCoin", tradingService.getUserStatusMap().get(authUser.getUserId()).getSelectCoin().name());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/v1/backdata")
    public List<BackData> getBackData() {
        return null;
    }
}


