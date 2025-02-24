package coin.cointrading.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {


    TRADING_ALREADY_GENERATE(HttpStatus.CONFLICT, "이미 프로그램이 동작중입니다."),
    TRADING_NOT_FOUND(HttpStatus.NOT_FOUND, "실행중인 프로그램이 없습니다."),

    AUTH_PASSWORD_BAD_REQUEST(HttpStatus.BAD_REQUEST, "비밀번호가 틀렸습니다."),
    AUTH_EXIST_ID(HttpStatus.CONFLICT, "이미 가입된 ID입니다."),
    AUTH_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지않는 ID입니다."),
    AUTH_INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "잘못된 API 키입니다. 발급된 키를 확인하세요."),
    AUTH_NO_AUTHORIZATION_IP(HttpStatus.UNAUTHORIZED, "허용되지 않은 IP입니다, 서버 IP로 키를 재발급해주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.status = httpStatus;
        this.message = message;
    }
}