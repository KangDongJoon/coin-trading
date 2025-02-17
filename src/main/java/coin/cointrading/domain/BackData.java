package coin.cointrading.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "backdatas")
public class BackData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String day;

    // 거래여부
    private String tradingStatus;

    // 수익률
    private double returnRate;

    public BackData(String day, String tradingStatus, double returnRate) {
        this.day = day;
        this.tradingStatus = tradingStatus;
        this.returnRate = returnRate;
    }
}
