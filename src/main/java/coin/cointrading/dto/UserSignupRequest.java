package coin.cointrading.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserSignupRequest {

    private String userId;
    private String password;
    private String userNickname;
    private String secretKey;
    private String accessKey;

}
