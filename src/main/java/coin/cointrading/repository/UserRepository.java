package coin.cointrading.repository;

import coin.cointrading.domain.Role;
import coin.cointrading.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserId(String userId);
    List<User> findByUserIdContainingIgnoreCase(String userId);
    @Query("SELECT u FROM User u WHERE u.role = :role")
    List<User> findByRole(@Param("role") Role role);

}

