package coin.cointrading.domain;

import coin.cointrading.exception.CustomException;
import coin.cointrading.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum Role {
    USER,
    ADMIN;

    public static Role of(String role) {
        return Arrays.stream(Role.values())
                .filter(r -> r.name().equalsIgnoreCase(role))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_BAD_REQUEST_ROLE));
    }
}
