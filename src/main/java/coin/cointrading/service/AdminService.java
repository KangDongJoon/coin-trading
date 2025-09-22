package coin.cointrading.service;

import coin.cointrading.domain.Role;
import coin.cointrading.domain.User;
import coin.cointrading.exception.CustomException;
import coin.cointrading.exception.ErrorCode;
import coin.cointrading.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;

    @Transactional
    public void changeRole(Long userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_USER_NOT_FOUND));

        user.changeRole(newRole);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> searchUsers(String field, String keyword) {
        switch (field) {
            case "id":
                try {
                    Long id = Long.parseLong(keyword);
                    return userRepository.findById(id)
                            .map(List::of)
                            .orElse(List.of());
                } catch (NumberFormatException e) {
                    return List.of();
                }
            case "userId":
                return userRepository.findByUserIdContainingIgnoreCase(keyword);
            case "role":
                return userRepository.findByRole(Role.of(keyword));
            default:
                return List.of();
        }
    }
}
