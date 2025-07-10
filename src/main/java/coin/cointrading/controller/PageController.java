package coin.cointrading.controller;

import coin.cointrading.domain.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    // 기본 페이지 = 비 인증자 = 로그인 페이지, 인증자 = 홈 페이지
    @GetMapping("/")
    public String mainToLoginPage(@AuthenticationPrincipal AuthUser authUser) {
        if (authUser != null) {
            return "redirect:/home";  // 인증된 사용자면 홈으로
        }
        return "redirect:/auth/login";  // 아니면 로그인 페이지로
    }


    // 로그인 페이지로 이동
    @GetMapping("/auth/login")
    public String showLoginPage() {
        return "login";
    }

    // 회원가입 페이지로 이동
    @GetMapping("/auth/signup")
    public String showSignupPage() {
        return "signup";
    }

    // 홈 페이지로 이동
    @GetMapping("/home")
    public String showHomePage() {
        return "home";
    }

    // 수익률 페이지로 이동
    @GetMapping("/auth/returnrate")
    public String showReturnPage() {
        return "returnrate";
    }

    // 가이드 페이지로 이동
    @GetMapping("/auth/guide")
    public String showGuidePage() {
        return "guide";
    }

}
