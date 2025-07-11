package coin.cointrading.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SchedulerControlService {
    private AtomicBoolean isProcessing = new AtomicBoolean(false); // 🔹 상태 변수

    public boolean getIsProcessing() {
        return isProcessing.get();
    }

    public void setIsProcessing(boolean updating) {
        this.isProcessing.set(updating);
    }
}
