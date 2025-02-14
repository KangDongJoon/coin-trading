package coin.cointrading.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AuthUser {

    private final String userId;
    private final String nickName;
    private final String upbitSecretKey;
    private final String upbitAccessKey;

}

