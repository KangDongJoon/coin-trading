package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.domain.User;
import coin.cointrading.dto.LoginRequest;
import coin.cointrading.dto.UserSignupRequest;
import coin.cointrading.exception.CustomException;
import coin.cointrading.exception.ErrorCode;
import coin.cointrading.repository.UserRepository;
import coin.cointrading.util.AES256Util;
import coin.cointrading.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class UserService {

    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final AES256Util aes256Util;
    private final RestTemplate restTemplate;

    @Transactional
    public void signup(UserSignupRequest request) throws Exception {
        // id를 통한 중복 가입 확인
        Optional<User> existingUser = userRepository.findByUserId(request.getUserId());

        if (existingUser.isPresent()) throw new CustomException(ErrorCode.AUTH_EXIST_ID);

        // 비밀번호 암호화
        String encodePassword = passwordEncoder.encode(request.getPassword());

        // API키 암호화
        String encodeSecret = aes256Util.encrypt(request.getSecretKey());
        String encodeAccess = aes256Util.encrypt(request.getAccessKey());

        AuthUser authUser = new AuthUser(request.getUserId(), request.getUserNickname(), encodeSecret, encodeAccess);

        // API키 확인
        String accountUrl = "https://api.upbit.com/v1/accounts";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("Authorization", jwtTokenProvider.createAccountToken(authUser));
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
            // 기타 예외 처리
            throw new RuntimeException("Upbit API 요청 실패: " + e.getMessage());
        }


        // 유저 객체 생성
        User user = new User(
                request.getUserId(),
                encodePassword,
                request.getUserNickname(),
                encodeSecret,
                encodeAccess
        );

        // 유저 DB 저장
        userRepository.save(user);
    }

    public String login(LoginRequest request) {
        // 유저 가입 확인
        User user = userRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_USER_NOT_FOUND));

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword()))
            throw new CustomException(ErrorCode.AUTH_PASSWORD_BAD_REQUEST);

        return jwtTokenProvider.createLoginToken(
                user.getUserId(),
                user.getUserNickname(),
                user.getUpbitSecretKey(),
                user.getUpbitAccessKey()
        );
    }
}
