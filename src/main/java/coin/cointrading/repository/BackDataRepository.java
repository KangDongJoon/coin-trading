package coin.cointrading.repository;

import coin.cointrading.domain.BackData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackDataRepository extends JpaRepository<BackData, Long> {

}
