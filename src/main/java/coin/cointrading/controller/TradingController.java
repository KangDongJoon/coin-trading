package coin.cointrading.controller;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.domain.BackData;
import coin.cointrading.dto.AccountResponse;
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
    public void startProgram(@AuthenticationPrincipal AuthUser authUser) {
        try {
            tradingService.startProgram(authUser);
        } catch (Exception e) {
            e.printStackTrace(); // 예외 출력
            throw new RuntimeException("Error starting program", e); // 예외 던지기
        }
    }

    @PostMapping("/v1/stops")
    public void stopProgram() {
        tradingService.stopProgram();
    }

    @GetMapping("/v1/status")
    public ResponseEntity<Map<String, String>> getStatus() {
        Map<String, String> status = new HashMap<>();
        boolean isRunning = tradingService.statusProgram(); // 프로그램 상태를 확인하는 로직
        status.put("isRunning", isRunning ? "true" : "false");
        return ResponseEntity.ok(status);
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


