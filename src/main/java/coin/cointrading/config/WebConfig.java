package coin.cointrading.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:8080") // ✅ 프론트엔드 도메인 지정
                        .allowedMethods("GET", "POST", "PUT", "DELETE")
                        .allowCredentials(true) // ✅ 쿠키 허용
                        .allowedHeaders("*");
            }
        };
    }
}
