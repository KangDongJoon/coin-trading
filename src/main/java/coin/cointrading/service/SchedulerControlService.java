package coin.cointrading.service;

import org.springframework.stereotype.Service;

@Service
public class SchedulerControlService {
    private volatile boolean isUpdatingTargetPrice = false; // 🔹 상태 변수

    public boolean isUpdatingTargetPrice() {
        return isUpdatingTargetPrice;
    }

    public void setUpdatingTargetPrice(boolean updating) {
        this.isUpdatingTargetPrice = updating;
    }
}
