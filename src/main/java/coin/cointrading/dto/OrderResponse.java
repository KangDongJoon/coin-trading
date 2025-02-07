package coin.cointrading.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderResponse {
    private String market;             // 마켓 (KRW-BTC)
    private String side;               // 주문 종류 (매수/매도)
    private String price;              // 주문 가격
    private String state;              // 주문 상태
    private String volume;             // 사용자가 입력한 주문량
    @JsonProperty("executed_volume")
    private String executedVolume;    // 체결된 양
    private String locked;             // 거래에 사용된 비용
}
