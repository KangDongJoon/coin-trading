package coin.cointrading.service;

import coin.cointrading.domain.Coin;
import coin.cointrading.domain.User;

public interface UpbitService {

    Object getAccount(User requestUser) throws Exception;

    Object orderCoins(String decision, User requestUser, Coin selectCoin) throws Exception;

    Object getOrders(User requestUser, int count, Coin selectCoin);
}
