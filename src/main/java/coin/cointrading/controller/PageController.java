package coin.cointrading.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {


    // 로그인 페이지로 이동
    @GetMapping("/")
    public String mainToLoginPage() {
        return "redirect:/auth/login";
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

    @GetMapping("/returnrate")
    public String showReturnPage() {
        return "returnrate";
    }

}
