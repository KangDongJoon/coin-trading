package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;

public interface UpbitService {

    Object getAccount(AuthUser authUser) throws Exception;

    Object orderCoins(String decision, AuthUser authUser) throws Exception;

    Object getOrders(AuthUser authUser, int count);
}
