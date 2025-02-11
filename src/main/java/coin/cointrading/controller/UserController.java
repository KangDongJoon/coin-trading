package coin.cointrading.controller;

import coin.cointrading.dto.UserSignupRequest;
import coin.cointrading.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 로그인 페이지로 이동
    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

    // 회원가입 페이지로 이동
    @GetMapping("/signup")
    public String showSignupPage() {
        return "signup";
    }

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@ModelAttribute UserSignupRequest request) {
        return userService.signup(request);
    }
}
