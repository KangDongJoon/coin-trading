package coin.cointrading.controller;

import coin.cointrading.dto.AccountResponse;
import coin.cointrading.service.TradingService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
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

    @PostMapping("/v1/starts")
    @Operation(summary = "프로그램실행", description = "프로그램실행")
    public void startProgram() throws IOException, InterruptedException, NoSuchAlgorithmException {
        tradingService.startProgram();
    }
}


