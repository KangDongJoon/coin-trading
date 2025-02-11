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

    private String upbitSecretKey;

    private String upbitAccessKey;

    public User(String userId, String password, String userNickname, String upbitSecretKey, String upbitAccessKey) {
        this.userId = userId;
        this.password = password;
        this.userNickname = userNickname;
        this.upbitSecretKey = upbitSecretKey;
        this.upbitAccessKey = upbitAccessKey;
    }

}
