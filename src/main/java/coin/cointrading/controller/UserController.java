package coin.cointrading.controller;

import coin.cointrading.dto.LoginRequest;
import coin.cointrading.dto.UserSignupRequest;
import coin.cointrading.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    @Value("${ec2.domain}")
    private String domain;
    private final UserService userService;

    @PostMapping("/signup")
    public String signup(@ModelAttribute UserSignupRequest request, RedirectAttributes redirectAttributes) {
        try {
            // 회원가입 성공 후, 로그인 페이지로 리디렉션
            userService.signup(request);
            redirectAttributes.addFlashAttribute("message", "회원가입 성공! 로그인하세요.");
            return "redirect:/auth/login";  // 성공 시 로그인 페이지로 리디렉션
        } catch (Exception e) {
            // 예외 발생 시, 회원가입 페이지로 리디렉션
            redirectAttributes.addFlashAttribute("error", "회원가입 실패! 다시 시도하세요.");
            return "redirect:/auth/signup";  // 실패 시 회원가입 페이지로 리디렉션
        }
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        try {
            String token = userService.login(request); // JWT 생성

            System.out.println("토큰생성");
            // HttpOnly, Secure 쿠키 설정
            ResponseCookie cookie = ResponseCookie.from("Authorization", token)
                    .httpOnly(true)   // JavaScript에서 접근 불가
                    .secure(false)
                    .path("/")        // 모든 경로에서 쿠키 사용 가능
                    .sameSite("Lax")
                    .domain(domain)
                    .build();
            System.out.println("쿠키생성");
            response.addHeader("Set-Cookie", cookie.toString());

            return ResponseEntity.ok("로그인 성공");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인 실패");
        }
    }


}
