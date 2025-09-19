package coin.cointrading.repository;

import coin.cointrading.domain.TradeInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<TradeInfo, Long> {

    List<TradeInfo> findByUserUserId(String userId);
}
