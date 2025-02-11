package coin.cointrading.filter;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.util.JwtAuthenticationToken;
import coin.cointrading.util.JwtTokenProvider;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
        String authorizationHeader = httpRequest.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String jwt = jwtTokenProvider.substringToken(authorizationHeader);
            try {
                DecodedJWT decodedJWT = jwtTokenProvider.extractClaims(jwt);  // DecodedJWT로 반환받기
                Long userId = Long.valueOf(decodedJWT.getSubject());  // userId는 subject로 반환됨

                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    String userNickname = decodedJWT.getClaim("nickName").asString();  // "name" 클레임

                    AuthUser authUser = new AuthUser(userId, userNickname);

                    JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(authUser);
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpRequest));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                } else {
                    log.error("JWT 파싱 실패: userId가 null이거나 SecurityContext에 이미 인증 정보가 설정됨");
                }

            } catch (JWTVerificationException e) {
                log.error("Invalid JWT token", e);
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "유효하지 않은 JWT 서명입니다.");
            } catch (Exception e) {
                log.error("Internal server error", e);
                httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
        chain.doFilter(httpRequest, httpResponse);
    }
}

