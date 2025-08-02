package coin.cointrading.repository;

import coin.cointrading.domain.TradeInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<TradeInfo, Long> {
}
