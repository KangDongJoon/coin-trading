package coin.cointrading.controller;

import coin.cointrading.dto.LoginRequest;
import coin.cointrading.dto.UserSignupRequest;
import coin.cointrading.exception.CustomException;
import coin.cointrading.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    @Value("${ec2.domain}")
    private String domain;
    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@ModelAttribute UserSignupRequest request) throws Exception {
        try {
            userService.signup(request);

            return ResponseEntity.ok().body("회원가입 성공");

        } catch (CustomException e) {
            return ResponseEntity.status(e.getErrorCode().getStatus()).body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        try {
            String token = userService.login(request); // JWT 생성

            // HttpOnly, Secure 쿠키 설정
            ResponseCookie cookie = ResponseCookie.from("Authorization", token)
                    .httpOnly(true)   // JavaScript에서 접근 불가
                    .secure(false)
                    .path("/")        // 모든 경로에서 쿠키 사용 가능
                    .sameSite("Lax")
                    .domain(domain)
                    .build();

            // 쿠키를 포함해 로그인
            response.addHeader("Set-Cookie", cookie.toString());

            return ResponseEntity.ok("로그인 성공");

        } catch (CustomException e) {
            return ResponseEntity.status(e.getErrorCode().getStatus()).body(e.getMessage());
        }
    }
}
