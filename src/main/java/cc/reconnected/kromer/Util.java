package cc.reconnected.kromer;

import net.luckperms.api.model.user.User;

public class Util {
    public static String getWalletAddress(User user) {
        return user.getCachedData().getMetaData().getMetaValue("wallet_address");
    }

    public static String getWalletPassword(User user) {
        return user.getCachedData().getMetaData().getMetaValue("wallet_password");
    }
}
