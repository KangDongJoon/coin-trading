package coin.cointrading.config;

import coin.cointrading.filter.JwtSecurityFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtSecurityFilter jwtSecurityFilter;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable)  // CSRF 비활성화 (프론트에서 API 요청을 허용)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 사용 안 함 (JWT 사용)
                .addFilterBefore(jwtSecurityFilter, SecurityContextHolderAwareRequestFilter.class) // JWT 필터 추가
                .formLogin(AbstractHttpConfigurer::disable) // formLogin 완전 비활성화 (프론트엔드 API 사용을 위해)
                .httpBasic(AbstractHttpConfigurer::disable) // HTTP 기본 인증 비활성화
                .logout(AbstractHttpConfigurer::disable) // 로그아웃 비활성화 (JWT 방식이므로 필요 없음)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/").permitAll() // ✅ 메인 페이지는 인증 없이 접근 가능
                        .requestMatchers("/auth/**", "/error").permitAll() // 로그인/회원가입 허용
                        .anyRequest().authenticated() // 그 외의 모든 요청은 인증 필요
                )
                .build();
    }
}
