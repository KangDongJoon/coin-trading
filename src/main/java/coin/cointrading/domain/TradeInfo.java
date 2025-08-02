package coin.cointrading.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Date;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "tradeinfos")
public class TradeInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDate tradingDay;

    private Coin tradeCoin;

    private double returnRate;

    private double beforeMoney;

    private double afterMoney;

    public TradeInfo(User user, LocalDate tradingDay, Coin tradeCoin, double returnRate, double beforeMoney, double afterMoney) {
        this.user = user;
        this.tradingDay = tradingDay;
        this.tradeCoin = tradeCoin;
        this.returnRate = returnRate;
        this.beforeMoney = beforeMoney;
        this.afterMoney = afterMoney;
    }

}
