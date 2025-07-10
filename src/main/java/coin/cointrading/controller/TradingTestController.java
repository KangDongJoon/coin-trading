package coin.cointrading.controller;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.domain.User;
import coin.cointrading.exception.CustomException;
import coin.cointrading.exception.ErrorCode;
import coin.cointrading.repository.UserRepository;
import coin.cointrading.service.TradingService;
import coin.cointrading.service.UpbitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TradingTestController {

    private final TradingService tradingService;
    private final UpbitService upbitService;
    private final UserRepository userRepository;

    // test API
    @PostMapping("/test/async")
    public void testAsync(@AuthenticationPrincipal AuthUser authUser) throws InterruptedException {
        tradingService.asyncTest(authUser);
        log.info("비동기 매수매도 실행-Controller");
    }

    @GetMapping("/test/op-change")
    public void opChange() {
        log.info("-----실행중인 유저 op_mode 변경-----");
        tradingService.opChange();
        log.info("-----op_mode 변경완료-----");
    }

    @GetMapping("/test/buy-change")
    public void holdChange() {
        log.info("-----실행중인 유저 buy_status 변경-----");
        tradingService.holdChange();
        log.info("-----hold 변경완료-----");
    }

    /**
     * 계좌 확인
     */
    @GetMapping("/v1/accounts")
    public ResponseEntity<Object> getAccount(@AuthenticationPrincipal AuthUser authUser) throws Exception {
        User requestUser = userRepository.findByUserId(authUser.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_USER_NOT_FOUND));
        return ResponseEntity.ok(upbitService.getAccount(requestUser));
    }
}
