package coin.cointrading.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String userId;

    private String password;

    private String userNickname;

    @Column(nullable = false)
    private String upbitSecretKey;

    @Column(nullable = false)
    private String upbitAccessKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    public User(String userId, String password, String userNickname, String upbitSecretKey, String upbitAccessKey, Role role) {
        this.userId = userId;
        this.password = password;
        this.userNickname = userNickname;
        this.upbitSecretKey = upbitSecretKey;
        this.upbitAccessKey = upbitAccessKey;
        this.role = role;
    }

    public void changeRole(Role newRole) {
        this.role = newRole;
    }
}
