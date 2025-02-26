package coin.cointrading.util;

import coin.cointrading.domain.AuthUser;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.*;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private String accessKey;
    private String secretKey;
    @Value("${jwt.secret.key}")
    private String jwtSecretKey;
    private final AES256Util aes256Util;

    public String createLoginToken(String userId, String userNickname, String upbitSecretKey, String upbitAccessKey) {
        Algorithm algorithm = Algorithm.HMAC256(jwtSecretKey);  // 비밀 키로 서명
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
        Date date = calendar.getTime(); // KST 기준으로 발급 시각 설정

        String jwtToken = JWT.create()
                .withSubject(String.valueOf(userId))
                .withClaim("userNickname", userNickname)
                .withClaim("upbitSecretKey", upbitSecretKey)
                .withClaim("upbitAccessKey", upbitAccessKey)
                .withIssuedAt(date)  // 발급일
                .withExpiresAt(new Date(date.getTime() + 3600000))  // 만료일: 1시간
                .sign(algorithm);  // 서명

        return jwtToken;
    }

    public String createAccountToken(AuthUser authUser) throws Exception {
        secretKey = aes256Util.decrypt(authUser.getUpbitSecretKey());
        accessKey = aes256Util.decrypt(authUser.getUpbitAccessKey());

        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String jwtToken = JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .sign(algorithm);

        return "Bearer " + jwtToken;
    }

    public String createOrderToken(HashMap<String, String> params, AuthUser authUser) throws Exception {
        secretKey = aes256Util.decrypt(authUser.getUpbitSecretKey());
        accessKey = aes256Util.decrypt(authUser.getUpbitAccessKey());

        ArrayList<String> queryElements = new ArrayList<>();
        for (Map.Entry<String, String> entity : params.entrySet()) {
            queryElements.add(entity.getKey() + "=" + entity.getValue());
        }

        String queryString = String.join("&", queryElements.toArray(new String[0]));

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(queryString.getBytes("UTF-8"));

        String queryHash = String.format("%0128x", new BigInteger(1, md.digest()));

        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String jwtToken = JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .withClaim("query_hash", queryHash)
                .withClaim("query_hash_alg", "SHA512")
                .sign(algorithm);

        return "Bearer " + jwtToken;
    }

    public String createGetOrderToken(String queryString, AuthUser authUser) throws Exception {
        secretKey = aes256Util.decrypt(authUser.getUpbitSecretKey());
        accessKey = aes256Util.decrypt(authUser.getUpbitAccessKey());

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(queryString.getBytes("UTF-8"));

        String queryHash = String.format("%0128x", new BigInteger(1, md.digest()));

        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String jwtToken = JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .withClaim("query_hash", queryHash)
                .withClaim("query_hash_alg", "SHA512")
                .sign(algorithm);

        return  "Bearer " + jwtToken;
    }

//    public String substringToken(String tokenValue) {
//        if (StringUtils.hasText(tokenValue) && tokenValue.startsWith(BEARER_PREFIX)) {
//            return tokenValue.substring(7);
//        }
//        throw new NoSuchElementException("Not Found Token");
//    }

    public DecodedJWT extractClaims(String token) {
        try {
            if (token == null || token.isBlank()) {
                throw new RuntimeException("토큰이 없습니다.");
            }

            // "Bearer " 접두어가 있으면 제거
            String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;

            Algorithm algorithm = Algorithm.HMAC256(jwtSecretKey);
            JWTVerifier verifier = JWT.require(algorithm).build();
            return verifier.verify(jwt);
        } catch (JWTVerificationException exception) {
            throw new RuntimeException("JWT 토큰 검증 실패", exception);
        }
    }
}
