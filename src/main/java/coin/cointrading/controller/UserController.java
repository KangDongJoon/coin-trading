package coin.cointrading.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserController {

    // 회원가입 페이지로 이동
    @GetMapping("/register")
    public String showRegisterPage() {
        return "register";  // register.html을 렌더링
    }

}
