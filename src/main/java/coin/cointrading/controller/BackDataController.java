package coin.cointrading.controller;

import coin.cointrading.domain.BackData;
import coin.cointrading.service.BackDataService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class BackDataController {

    private final BackDataService backDataService;

    @GetMapping("/auth/get-back-data")
    public List<BackData> getBackData() {
        return backDataService.getBackData();
    }
}
