package coin.cointrading.controller;

import coin.cointrading.dto.AccountResponse;
import coin.cointrading.service.TradingService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class TradingController {

    private final TradingService tradingService;

    @GetMapping("/v1/accounts")
    @Operation(summary = "계좌조회", description = "내 계좌 조회")
    public ResponseEntity<List<AccountResponse>> getAccount() {
        return ResponseEntity.ok(tradingService.getAccount());
    }

    @GetMapping("/v1/ai-decisions")
    @Operation(summary = "AI 투자 판단", description = "AI 투자 판단")
    public ResponseEntity<String> aiDecision() throws IOException {
        return ResponseEntity.ok(tradingService.aiDecision());
    }

}


