package coin.cointrading.controller;

import coin.cointrading.dto.OrderResponse;
import coin.cointrading.service.GptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

@RestController
@RequiredArgsConstructor
public class GptController {

    private final GptService gptService;

    @GetMapping("/v1/gpt/decisions")
    public ResponseEntity<String> aiDecision() throws IOException {
        return ResponseEntity.ok(gptService.aiDecision());
    }

//    @PostMapping("/v1/gpt/orders")
//    public ResponseEntity<OrderResponse> orderCoin() throws IOException, NoSuchAlgorithmException {
//        return ResponseEntity.ok(gptService.orderCoin());
//    }
}
