package coin.cointrading.filter;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.util.JwtAuthenticationToken;
import coin.cointrading.util.JwtTokenProvider;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
@Component
public class JwtSecurityFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest httpRequest,
            @NonNull HttpServletResponse httpResponse,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {
        String requestURI = httpRequest.getRequestURI();

        // 인증이 필요 없는 URL이라면 필터를 건너뜀
        if (requestURI.equals("/") || requestURI.equals("/auth/login") || requestURI.equals("/auth/signup") || requestURI.equals("/auth/guide") || requestURI.startsWith("/error")) {
            chain.doFilter(httpRequest, httpResponse);
            return;
        }

        String jwt = resolveToken(httpRequest); // 여기서 JWT 가져오기

        if (jwt != null) {
            try {
                DecodedJWT decodedJWT = jwtTokenProvider.extractClaims(jwt);  // JWT 검증 및 파싱
                String userId = decodedJWT.getSubject();

                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    String userNickname = decodedJWT.getClaim("userNickname").asString();
                    String upbitSecretKey = decodedJWT.getClaim("upbitSecretKey").asString();
                    String upbitAccessKey = decodedJWT.getClaim("upbitAccessKey").asString();

                    AuthUser authUser = new AuthUser(userId, userNickname, upbitSecretKey, upbitAccessKey);

                    JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(authUser);
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpRequest));

                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                } else {
                    log.error("JWT 파싱 실패: userId가 null이거나 SecurityContext에 이미 인증 정보가 설정됨");
                }
            } catch (Exception e) {
                log.error("Internal server error", e);
                httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }
        }
        chain.doFilter(httpRequest, httpResponse);
    }

    // 쿠키에서 JWT 토큰을 추출
    private String resolveToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("Authorization".equals(cookie.getName())) {
                    return cookie.getValue();  // Authorization 쿠키에서 토큰 추출
                }
            }
        }
        return null;
    }
}


