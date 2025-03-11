package coin.cointrading.service;

import coin.cointrading.domain.User;
import coin.cointrading.dto.LoginRequest;
import coin.cointrading.dto.UserSignupRequest;
import coin.cointrading.exception.CustomException;
import coin.cointrading.repository.UserRepository;
import coin.cointrading.util.AES256Util;
import coin.cointrading.util.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private AES256Util aes256Util;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RedisService redisService;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void signup_success() throws Exception {
        // given
        UserSignupRequest request = new UserSignupRequest("test1", "test1password", "nick1", "secret", "access");
        String accountUrl = "https://api.upbit.com/v1/accounts";
        when(userRepository.findByUserId(request.getUserId())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(aes256Util.encrypt(request.getSecretKey())).thenReturn("encryptedSecret");
        when(aes256Util.encrypt(request.getAccessKey())).thenReturn("encryptedAccess");

        // Upbit API 응답 설정
        ResponseEntity<String> response = new ResponseEntity<>("[]", HttpStatus.OK);
        when(restTemplate.exchange(
                eq(accountUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(response);

        // when
        userService.signup(request);

        // then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUserId()).isEqualTo(request.getUserId());
        assertThat(savedUser.getPassword()).isEqualTo("encodedPassword");
        assertThat(savedUser.getUserNickname()).isEqualTo(request.getUserNickname());
        assertThat(savedUser.getUpbitSecretKey()).isEqualTo("encryptedSecret");
        assertThat(savedUser.getUpbitAccessKey()).isEqualTo("encryptedAccess");
    }

    @Test
    void signup_fail_duplicatedId() {
        // given
        UserSignupRequest request = new UserSignupRequest("test1", "test1password", "nick1", "secret1", "access1");
        User user1 = new User();
        when(userRepository.findByUserId("test1")).thenReturn(Optional.of(user1));

        // when, then
        CustomException exception = assertThrows(CustomException.class, () -> userService.signup(request));
        assertThat(exception.getMessage()).isEqualTo("이미 가입된 ID입니다.");
    }

    @Test
    void signup_fail_invalid_apikey() throws Exception {
        // given
        UserSignupRequest request = new UserSignupRequest("test1", "test1password", "nick1", "secret", "access");
        String accountUrl = "https://api.upbit.com/v1/accounts";
        when(userRepository.findByUserId(request.getUserId())).thenReturn(Optional.empty());

        when(restTemplate.exchange(
                eq(accountUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                HttpHeaders.EMPTY,
                "{\"error\":{\"name\":\"invalid_access_key\"}}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8 // 인코딩
        ));

        // when & then
        CustomException exception = assertThrows(CustomException.class, () -> userService.signup(request));
        assertThat(exception.getMessage()).isEqualTo("잘못된 API 키입니다. 발급된 키를 확인하세요.");
    }

    @Test
    void signup_fail_no_authorization_ip() throws Exception {
        // given
        UserSignupRequest request = new UserSignupRequest("test1", "test1password", "nick1", "secret", "access");
        String accountUrl = "https://api.upbit.com/v1/accounts";
        when(userRepository.findByUserId(request.getUserId())).thenReturn(Optional.empty());

        when(restTemplate.exchange(
                eq(accountUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                HttpHeaders.EMPTY,
                "{\"error\":{\"name\":\"no_authorization_ip\"}}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8 // 인코딩
        ));

        // when & then
        CustomException exception = assertThrows(CustomException.class, () -> userService.signup(request));
        assertThat(exception.getMessage()).isEqualTo("허용되지 않은 IP입니다, 서버 IP로 키를 재발급해주세요.");
    }

    @Test
    void login_success() {
        // given
        LoginRequest request = new LoginRequest("test1", "test1password");
        User user = new User("test1", "test1password", "nick1", "secret1", "access1");
        when(userRepository.findByUserId(request.getUserId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);
        when(jwtTokenProvider.createRefreshToken(user.getUserId())).thenReturn("refreshTokenMock");
        String testToken = "loginToken";
        when(jwtTokenProvider.createAccessToken(
                user.getUserId(),
                user.getUserNickname(),
                user.getUpbitSecretKey(),
                user.getUpbitAccessKey()
        )).thenReturn(testToken);
        // when
        String loginToken = userService.login(request);

        // then
        assertThat(loginToken).isEqualTo(testToken);
    }

    @Test
    void login_fail_id_not_found() {
        // given
        LoginRequest request = new LoginRequest("test1", "test1password");
        when(userRepository.findByUserId(request.getUserId())).thenReturn(Optional.empty());

        // when, then
        CustomException exception = assertThrows(CustomException.class, () -> userService.login(request));
        assertThat(exception.getMessage()).isEqualTo("존재하지않는 ID입니다.");
    }

    @Test
    void login_fail_unmatched_password() {
        // given
        LoginRequest request = new LoginRequest("test1", "failpassword");
        User user = new User("test1", "test1password", "nick1", "secret1", "access1");
        when(userRepository.findByUserId(request.getUserId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(Objects.equals(request.getPassword(), user.getPassword()));

        // when, then
        CustomException exception = assertThrows(CustomException.class, () -> userService.login(request));
        assertThat(exception.getMessage()).isEqualTo("비밀번호가 틀렸습니다.");
    }
}
