package coin.cointrading.controller;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.dto.AccountResponse;
import coin.cointrading.service.TradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TradingController {

    private final TradingService tradingService;

    @GetMapping("/v1/accounts")
    public ResponseEntity<List<AccountResponse>> getAccount(@AuthenticationPrincipal AuthUser authUser) throws Exception {
        return ResponseEntity.ok(tradingService.getAccount(authUser));
    }

    @PostMapping("/v1/starts")
    public void startProgram(@AuthenticationPrincipal AuthUser authUser) throws Exception {
        tradingService.startProgram(authUser);
    }

    @PostMapping("/v1/stops")
    public void stopProgram(){
        tradingService.stopProgram();
    }
}


