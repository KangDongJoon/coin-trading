package coin.cointrading.controller;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.dto.LoginRequest;
import coin.cointrading.dto.UserSignupRequest;
import coin.cointrading.exception.CustomException;
import coin.cointrading.service.RedisService;
import coin.cointrading.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RedisService redisService;

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
                    .build();

            // 쿠키를 포함해 로그인
            response.addHeader("Set-Cookie", cookie.toString());

            return ResponseEntity.ok("로그인 성공");

        } catch (CustomException e) {
            return ResponseEntity.status(e.getErrorCode().getStatus()).body(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@AuthenticationPrincipal AuthUser authUser, HttpServletResponse response) {
        try {
            // Redis에서 RefreshToken 삭제
            redisService.deleteRefreshToken(authUser.getUserId());

            // 쿠키에서 JWT 토큰 삭제
            ResponseCookie cookie = ResponseCookie.from("Authorization", "")
                    .httpOnly(true)
                    .secure(true)   // 운영 환경에서는 보통 secure=true로 설정
                    .path("/")
                    .sameSite("Lax")
                    .maxAge(0)  // 쿠키 만료시킴
                    .build();

            response.addHeader("Set-Cookie", cookie.toString());  // 쿠키 삭제

            return ResponseEntity.ok("로그아웃 성공");
        } catch (Exception e) {
            return ResponseEntity.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).body("로그아웃 실패");
        }
    }

}
