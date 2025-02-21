package coin.cointrading.service;

import coin.cointrading.domain.User;
import coin.cointrading.dto.UserSignupRequest;
import coin.cointrading.exception.CustomException;
import coin.cointrading.repository.UserRepository;
import coin.cointrading.util.AES256Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this); // Mockito 어노테이션 초기화
    }
    @Test
    void signup() throws Exception {
        // given
        UserSignupRequest request = new UserSignupRequest("test1", "test1password", "nick1", "secret", "access");

        when(userRepository.findByUserId(request.getUserId())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(aes256Util.encrypt(request.getSecretKey())).thenReturn("encryptedSecret");
        when(aes256Util.encrypt(request.getAccessKey())).thenReturn("encryptedAccess");

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
    void duplicatedId() {
        // given
        UserSignupRequest request = new UserSignupRequest("test1", "test1password", "nick1", "secret1", "access1");
        User user1 = new User();
        when(userRepository.findByUserId("test1")).thenReturn(Optional.of(user1));

        // when, then
        CustomException exception = assertThrows(CustomException.class, () -> userService.signup(request));
        assertThat(exception.getMessage()).isEqualTo("이미 가입된 ID입니다.");
    }
}
