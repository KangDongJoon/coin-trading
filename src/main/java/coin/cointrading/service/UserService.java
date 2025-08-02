package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.domain.TradeInfo;
import coin.cointrading.domain.User;
import coin.cointrading.dto.LoginRequest;
import coin.cointrading.dto.TradeInfoResponse;
import coin.cointrading.dto.UserSignupRequest;
import coin.cointrading.exception.CustomException;
import coin.cointrading.exception.ErrorCode;
import coin.cointrading.repository.TradeRepository;
import coin.cointrading.repository.UserRepository;
import coin.cointrading.util.AES256Util;
import coin.cointrading.util.JwtTokenProvider;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class UserService {

    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final TradeRepository tradeRepository;
    private final AES256Util aes256Util;
    private final RestTemplate restTemplate;
    private final RedisService redisService;

    /**
     * 회원가입
     * @param request id, pw, nickname, upbitSecretKey, upbitAccessKey
     */
    @Transactional
    public void signup(UserSignupRequest request) throws Exception {
        // id를 통한 중복 가입 확인
        Optional<User> existingUser = userRepository.findByUserId(request.getUserId());

        if (existingUser.isPresent()) throw new CustomException(ErrorCode.AUTH_EXIST_ID);

        // 비밀번호 암호화
        String encodePassword = passwordEncoder.encode(request.getPassword());

        // 업비트 API 키 유효 여부 확인
        validateUpbitApiKey(request);

        // API키 암호화
        String encodeSecret = aes256Util.encrypt(request.getSecretKey());
        String encodeAccess = aes256Util.encrypt(request.getAccessKey());

        // 유저 객체 생성 및 DB 저장
        userRepository.save(new User(
                request.getUserId(),
                encodePassword,
                request.getUserNickname(),
                encodeSecret,
                encodeAccess
        ));
    }

    /**
     * 로그인
     * @param request id, pw
     * @return 로그인 토큰
     */
    public String login(LoginRequest request) {
        // 유저 가입 확인
        User user = userRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_USER_NOT_FOUND));

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword()))
            throw new CustomException(ErrorCode.AUTH_PASSWORD_BAD_REQUEST);

        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());
        redisService.saveRefreshToken(user.getUserId(), refreshToken, 7 * 24 * 60 * 60);

        return jwtTokenProvider.createAccessToken(
                user.getUserId(),
                user.getUserNickname()
        );
    }

    /**
     * Upbit API 키 확인 메서드
     * @param request 가입 요청 폼
     */
    private void validateUpbitApiKey(UserSignupRequest request) {
        // API키 확인
        String accountUrl = "https://api.upbit.com/v1/accounts";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        headers.set("Authorization", jwtTokenProvider.createAccountToken(request.getAccessKey(), request.getSecretKey()));
        HttpEntity<?> entity = new HttpEntity<>(headers);
        try {
            restTemplate.exchange(accountUrl, HttpMethod.GET, entity, String.class);
        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            if (responseBody.contains("invalid_")) {
                throw new CustomException(ErrorCode.AUTH_INVALID_API_KEY);
            } else if (responseBody.contains("no_authorization_ip")) {
                throw new CustomException(ErrorCode.AUTH_NO_AUTHORIZATION_IP);
            }
        } catch (Exception e) {
            throw new RuntimeException("Upbit API 요청 실패: " + e.getMessage());
        }
    }

    public String tokenRefresh(String refreshToken) {
        DecodedJWT decodedRefreshToken = jwtTokenProvider.extractClaims(refreshToken);
        String userId = decodedRefreshToken.getSubject();

        // 3) Redis에 저장된 Refresh Token과 비교
        String savedRefreshToken = redisService.getRefreshToken(userId);
        if (!refreshToken.equals(savedRefreshToken)) {
            throw new CustomException(ErrorCode.AUTH_NO_AUTHORIZATION_USER);
        }

        // 4) 새로운 Access Token 생성
        String userNickname = decodedRefreshToken.getClaim("userNickname").asString();
        String newAccessToken = jwtTokenProvider.createAccessToken(userId, userNickname);

        return ResponseCookie.from("Authorization", newAccessToken)
                .httpOnly(true)
                .secure(false)  // https 환경에서만
                .path("/")
                .sameSite("Lax")
                .build().toString();
    }

    public List<TradeInfoResponse> getMyPageData(AuthUser authUser) {
        User user = getRequestUserByIdOrThrow(authUser);
        return tradeRepository.findByUserUserId(user.getUserId())
                .stream()
                .map(tradeInfo -> new TradeInfoResponse(
                        tradeInfo.getTradingDay().toString(),
                        tradeInfo.getTradeCoin(),
                        String.format("%.2f%%", tradeInfo.getReturnRate() * 100),
                        (int)tradeInfo.getBeforeMoney(),
                        (int)tradeInfo.getAfterMoney()
                )).toList();
    }

    private User getRequestUserByIdOrThrow(AuthUser authUser) {
        return userRepository.findByUserId(authUser.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_USER_NOT_FOUND));
    }
}
