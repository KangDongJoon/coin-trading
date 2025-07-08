package coin.cointrading.repository;

import coin.cointrading.domain.BackData;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BackDataRepository extends JpaRepository<BackData, Long> {
    @Query("SELECT d FROM BackData d WHERE d.coin = :coin ORDER BY d.day DESC")
    List<BackData> findLatestData(Pageable pageable, String coin);

    List<BackData> findByCoinOrderByDayDesc(String coin);
}
