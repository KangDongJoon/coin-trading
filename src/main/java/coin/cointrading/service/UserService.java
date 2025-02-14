package coin.cointrading.service;

import coin.cointrading.domain.User;
import coin.cointrading.dto.LoginRequest;
import coin.cointrading.dto.UserSignupRequest;
import coin.cointrading.repository.UserRepository;
import coin.cointrading.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class UserService {

    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Transactional
    public void signup(UserSignupRequest request) {
        // email을 통한 중복 가입 확인
        Optional<User> existingUser = userRepository.findByUserId(request.getUserId());
        if (existingUser.isPresent()) {
            throw new IllegalStateException("이미 가입된 아이디입니다.");
        }

        // 비밀번호 암호화
        String encodePassword = passwordEncoder.encode(request.getPassword());

        // 유저 객체 생성
        User user = new User(
                request.getUserId(),
                encodePassword,
                request.getUserNickname(),
                request.getSecretKey(),
                request.getAccessKey()
        );

        // 유저 DB 저장
        userRepository.save(user);
    }

    public String login(LoginRequest request) {

        User user = userRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("유저가없습니다."));

        // 비밀번호 비교
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // 비밀번호가 틀린 경우, BadCredentialsException을 발생
            throw new BadCredentialsException("비밀번호가 틀렸습니다.");
        }

        String token = jwtTokenProvider.createLoginToken(
                user.getId(),
                user.getUserNickname()
        );
        return token;
    }
}
