package coin.cointrading.service;

import coin.cointrading.domain.AuthUser;
import coin.cointrading.domain.Coin;

public interface UpbitService {

    Object getAccount(AuthUser authUser) throws Exception;

    Object orderCoins(String decision, AuthUser authUser, Coin selectCoin) throws Exception;

    Object getOrders(AuthUser authUser, int count, Coin selectCoin);
}
