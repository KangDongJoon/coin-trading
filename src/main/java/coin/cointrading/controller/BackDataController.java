package coin.cointrading.controller;

import coin.cointrading.domain.BackData;
import coin.cointrading.domain.Coin;
import coin.cointrading.service.BackDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/backdatas")
public class BackDataController {

    private final BackDataService backDataService;

    @GetMapping
    public List<BackData> getBackData(@RequestParam(defaultValue = "BTC") Coin coin,
                                      @RequestParam(defaultValue = "7") int days) {

        List<BackData> backData = backDataService.getBackDataMap().get(coin);
        // 해당 코인 불러오기
        return backData.stream()
                .limit(days)
                .toList();
    }
}
