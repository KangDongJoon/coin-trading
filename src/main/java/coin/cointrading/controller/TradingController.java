package coin.cointrading.controller;

import coin.cointrading.dto.AccountResponse;
import coin.cointrading.service.TradingService;
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
    public ResponseEntity<List<AccountResponse>> getAccount() {
        return ResponseEntity.ok(tradingService.getAccount());
    }

    @PostMapping("/v1/starts")
    public void startProgram() throws IOException, InterruptedException, NoSuchAlgorithmException {
        tradingService.startProgram();
    }

    @PostMapping("/v1/stops")
    public void stopProgram(){
        tradingService.stopProgram();
    }
}


