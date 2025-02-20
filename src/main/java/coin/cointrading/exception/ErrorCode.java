package coin.cointrading.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    AUTH_PASSWORD_BAD_REQUEST(HttpStatus.BAD_REQUEST, "비밀번호가 틀렸습니다."),
    AUTH_EXIST_ID(HttpStatus.CONFLICT, "이미 가입된 ID입니다."),
    AUTH_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지않는 ID입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.status = httpStatus;
        this.message = message;
    }
}