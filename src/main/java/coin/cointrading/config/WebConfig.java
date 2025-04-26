package coin.cointrading.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {
    @Value("${allowed.origins}")
    private String allowedOriginsUrl;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NotNull CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(allowedOriginsUrl)
                        .allowedMethods("GET", "POST", "PUT", "DELETE")
                        .allowCredentials(true) // ✅ 쿠키 허용
                        .allowedHeaders("*");
            }
        };
    }

}
