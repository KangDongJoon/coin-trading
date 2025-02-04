package coin.cointrading.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    @Value("${upbit.access-key}")
    private String accessKey;
    @Value("${upbit.secret-key}")
    private String secretKey;

    public String createToken(){
        long expirationTime = 1000 * 60 * 60;  // 1 hour
        Date expirationDate = new Date(System.currentTimeMillis() + expirationTime);

        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String jwtToken = JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .withExpiresAt(expirationDate)
                .sign(algorithm);

        return "Bearer " + jwtToken;
    }
}
