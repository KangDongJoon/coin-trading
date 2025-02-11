package coin.cointrading.domain;

import lombok.Getter;

@Getter
public class AuthUser {

    private final Long userId;
    private final String nickName;

    public AuthUser(Long userId, String nickName) {
        this.userId = userId;
        this.nickName = nickName;
    }
}

