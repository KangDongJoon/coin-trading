package coin.cointrading.util;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.service.RedisService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret.key}")
    private String jwtSecretKey;
    private final AES256Util aes256Util;
    private final RedisService redisService;

    public String createRefreshToken(String userId) {
        Algorithm algorithm = Algorithm.HMAC256(jwtSecretKey);  // 비밀 키로 서명
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
        Date date = calendar.getTime(); // KST 기준으로 발급 시각 설정

        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withIssuedAt(date)  // 발급일
                .sign(algorithm); // 서명
    }

    public String createAccessToken(String userId, String userNickname, String upbitSecretKey, String upbitAccessKey) {
        Algorithm algorithm = Algorithm.HMAC256(jwtSecretKey);  // 비밀 키로 서명
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
        Date date = calendar.getTime(); // KST 기준으로 발급 시각 설정

        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withClaim("userNickname", userNickname)
                .withClaim("upbitSecretKey", upbitSecretKey)
                .withClaim("upbitAccessKey", upbitAccessKey)
                .withIssuedAt(date)  // 발급일
                .withExpiresAt(new Date(date.getTime() + 3600000))  // 만료일: 1시간
                .sign(algorithm);
    }

    public String createAccountToken(AuthUser authUser) throws Exception {
        String accessKey  = aes256Util.decrypt(authUser.getUpbitAccessKey());
        String secretKey = aes256Util.decrypt(authUser.getUpbitSecretKey());

        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String jwtToken = JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .sign(algorithm);

        return "Bearer " + jwtToken;
    }

    public String createOrderToken(HashMap<String, String> params, AuthUser authUser) throws Exception {
        String accessKey  = aes256Util.decrypt(authUser.getUpbitAccessKey());
        String secretKey = aes256Util.decrypt(authUser.getUpbitSecretKey());

        ArrayList<String> queryElements = new ArrayList<>();
        for (Map.Entry<String, String> entity : params.entrySet()) {
            queryElements.add(entity.getKey() + "=" + entity.getValue());
        }

        String queryString = String.join("&", queryElements.toArray(new String[0]));

        String jwtToken = getJwtToken(queryString, secretKey, accessKey);

        return "Bearer " + jwtToken;
    }

    public String createGetOrderToken(String queryString, AuthUser authUser) throws Exception {
        String accessKey  = aes256Util.decrypt(authUser.getUpbitAccessKey());
        String secretKey = aes256Util.decrypt(authUser.getUpbitSecretKey());

        String jwtToken = getJwtToken(queryString, secretKey, accessKey);

        return "Bearer " + jwtToken;
    }

    public DecodedJWT extractClaims(String token) {
        try {
            if (token == null || token.isBlank()) {
                throw new RuntimeException("토큰이 없습니다.");
            }

            // "Bearer " 접두어가 있으면 제거
            String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;

            Algorithm algorithm = Algorithm.HMAC256(jwtSecretKey);
            JWTVerifier verifier = JWT.require(algorithm).build();

            DecodedJWT decodedJWT = JWT.decode(jwt);
            String userId = decodedJWT.getSubject();
            Date expiresAt = decodedJWT.getExpiresAt();
            // accessToken 만료 확인 후 refreshToken 확인
            if (expiresAt.before(new Date())) {
                String refreshToken = redisService.getRefreshToken(userId);
                if (refreshToken != null) {
                    String userNickname = decodedJWT.getClaim("userNickname").asString();
                    String upbitSecretKey = decodedJWT.getClaim("upbitSecretKey").asString();
                    String upbitAccessKey = decodedJWT.getClaim("upbitAccessKey").asString();
                    String newAccessToken = createAccessToken(
                            userId,
                            userNickname,
                            upbitSecretKey,
                            upbitAccessKey
                    );
                    return verifier.verify(newAccessToken);
                }
            }
            return verifier.verify(jwt);
        } catch (JWTVerificationException exception) {
            throw new RuntimeException("JWT 토큰 검증 실패", exception);
        }
    }

    private static String getJwtToken(String queryString, String secretKey, String accessKey) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(queryString.getBytes(StandardCharsets.UTF_8));

        String queryHash = String.format("%0128x", new BigInteger(1, md.digest()));

        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        return JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .withClaim("query_hash", queryHash)
                .withClaim("query_hash_alg", "SHA512")
                .sign(algorithm);
    }

}
