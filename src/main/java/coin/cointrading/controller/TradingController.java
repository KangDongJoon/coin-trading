package coin.cointrading.controller;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.domain.BackData;
import coin.cointrading.service.TradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TradingController {

    private final TradingService tradingService;

    @PostMapping("/v1/starts")
    public String startProgram(@AuthenticationPrincipal AuthUser authUser) {
        try {
            // executeTrade 메서드 비동기 호출
            tradingService.startTrading(authUser);
            return "매매 프로그램이 정상적으로 실행되었습니다.";
        } catch (Exception e) {
            return "매매 프로그램 실행 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    @PostMapping("/v1/stops")
    public void stopProgram(@AuthenticationPrincipal AuthUser authUser) {
        tradingService.stopTrading(authUser);
    }

    // 상태 확인 API
    @GetMapping("/v1/status")
    public ResponseEntity<Map<String, String>> checkStatus(@AuthenticationPrincipal AuthUser authUser) {
        String status = tradingService.checkStatus(authUser); // 사용자 상태 반환
        Map<String, String> response = new HashMap<>();
        response.put("isRunning", status);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/v1/initbackdata")
    public void initBackData() throws IOException {
        tradingService.initBackData();
    }

    @PostMapping("/v1/backdata")
    public void postBackData() throws IOException {
        tradingService.postBackData();
    }

    @GetMapping("/v1/backdata")
    public List<BackData> getBackData() {
        return tradingService.getBackData();
    }
}


