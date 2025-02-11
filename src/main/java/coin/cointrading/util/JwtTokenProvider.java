package coin.cointrading.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Component
public class JwtTokenProvider {

    @Value("${upbit.access-key}")
    private String accessKey;
    @Value("${upbit.secret-key}")
    private String secretKey;
    @Value("${jwt.secret.key}")
    private String jwtSecretKey;
    private static final String BEARER_PREFIX = "Bearer ";

    public String createLoginToken(Long userId, String userNickname) {
        Algorithm algorithm = Algorithm.HMAC256(jwtSecretKey);  // 비밀 키로 서명
        Date date = new Date();  // 발급 시각

        String jwtToken = JWT.create()
                .withSubject(String.valueOf(userId))  // userId를 subject로 설정
                .withClaim("userNickname", userNickname)  // username을 claim으로 추가
                .withIssuedAt(date)  // 발급일
                .withExpiresAt(new Date(date.getTime() + 3600000))  // 만료일: 1시간
                .sign(algorithm);  // 서명

        return "Bearer " + jwtToken;
    }

    public String createAccountToken() {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String jwtToken = JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .sign(algorithm);

        return "Bearer " + jwtToken;
    }

    public String createOrderToken(HashMap<String, String> params) throws NoSuchAlgorithmException, UnsupportedEncodingException {
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

    public String substringToken(String tokenValue) {
        if (StringUtils.hasText(tokenValue) && tokenValue.startsWith(BEARER_PREFIX)) {
            return tokenValue.substring(7);
        }
        throw new NoSuchElementException("Not Found Token");
    }

    public DecodedJWT extractClaims(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secretKey);
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token.replace("Bearer ", ""));
            return jwt;  // 전체 JWT 반환 (클레임은 getClaim 메서드를 통해 추출 가능)
        } catch (JWTVerificationException exception) {
            throw new RuntimeException("JWT 토큰 검증 실패", exception);
        }
    }
}
