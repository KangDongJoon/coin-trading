package coin.cointrading.repository;

import coin.cointrading.domain.BackData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BackDataRepository extends JpaRepository<BackData, Long> {

    @Query("SELECT b FROM BackData b WHERE b.tradingStatus = 'O'")
    List<BackData> findAllActiveTrading();

}
