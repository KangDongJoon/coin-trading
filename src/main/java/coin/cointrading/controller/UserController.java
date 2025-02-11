package coin.cointrading.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserController {

    // 로그인 페이지로 이동
    @GetMapping("/auth/login")
    public String showLoginPage() {
        return "login";
    }

    // 회원가입 페이지로 이동
    @GetMapping("/auth/register")
    public String showRegisterPage() {
        return "register";
    }

}
