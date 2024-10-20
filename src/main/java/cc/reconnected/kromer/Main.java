package cc.reconnected.kromer;

import cc.reconnected.kromer.commands.BalanceCommand;
import cc.reconnected.kromer.responses.WalletCreateResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.event.user.UserFirstLoginEvent;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.types.MetaNode;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

public class Main implements ModInitializer {
    static Logger LOGGER = LoggerFactory.getLogger("rcc-kromer");
    public static LuckPerms luckPerms;
    public static UserManager userManager;
    public static GroupManager groupManager;
    public static String kromerURL;
    public static RccKromerConfig config;
    public static HttpClient httpclient = HttpClient.newHttpClient();
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> onStartServer());

        CommandRegistrationCallback.EVENT.register(KromerCommand::register);
        CommandRegistrationCallback.EVENT.register(BalanceCommand::register);

        config = RccKromerConfig.createAndLoad();
    }

    public void onStartServer() {
        luckPerms = LuckPermsProvider.get();
        userManager = luckPerms.getUserManager();
        groupManager = luckPerms.getGroupManager();
        luckPerms.getEventBus().subscribe(UserFirstLoginEvent.class, Main::firstLogin);
    }

    public static void firstLogin(UserFirstLoginEvent userFirstLoginEvent) {
        firstLogin(userFirstLoginEvent.getUsername(), userFirstLoginEvent.getUniqueId());
    }
    public static void firstLogin(String username, UUID uuid) {
        JsonObject playerObject = new JsonObject();
        playerObject.addProperty("username", username);
        playerObject.addProperty("uuid", uuid.toString());
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder().uri(new URI(config.KromerURL()+"api/_internal/wallet/create")).headers("Kromer-Key", config.KromerKey(), "Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(playerObject.toString())).build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        httpclient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
            nuhuh(response, throwable);
            WalletCreateResponse walletResponse = new Gson().fromJson(response.body(), WalletCreateResponse.class);
            MetaNode node = MetaNode.builder("wallet_address", walletResponse.address).build();
            MetaNode node2 = MetaNode.builder("wallet_password", walletResponse.password).build();
            luckPerms.getUserManager().modifyUser(uuid, user -> {
                user.data().add(node);
                user.data().add(node2);
            }).join();
            generateMoney(uuid, 100);
        }).join();
    }
    public static boolean generateMoney(UUID uuid, float amount) {
        CachedMetaData playerData = luckPerms.getUserManager().getUser(uuid).getCachedData().getMetaData();
        String walletAddress = playerData.getMetaValue("wallet_address");
        JsonObject moneyGenObject = new JsonObject();
        moneyGenObject.addProperty("address", walletAddress);
        moneyGenObject.addProperty("amount", amount);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder().uri(new URI(kromerURL + "api/_internal/give-money")).POST(HttpRequest.BodyPublishers.ofString(moneyGenObject.toString())).build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        httpclient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete(Main::nuhuh).join();
        return true;
    }

    public static void nuhuh(HttpResponse<String> response, Throwable throwable) {
        if (throwable != null) {
            LOGGER.error("Failed to send player data to Kromer", throwable);
            return;
        }
        if (response.statusCode() != 200) {
            LOGGER.error("Failed to send player data to Kromer: " + response.body());
            return;
        }
        if (response.body() == null) {
            LOGGER.error("Failed to send player data to Kromer: No response body");
            return;
        }
    }

    public static boolean createTransaction(ServerPlayerEntity sendingPlayer, String address, float amount) {
        HttpRequest request;
        CachedMetaData sendingData =  luckPerms.getUserManager().getUser(sendingPlayer.getUuid()).getCachedData().getMetaData();
        String walletAddress = sendingData.getMetaValue("wallet_address");
        String walletPassword = sendingData.getMetaValue("wallet_password");
        try {
            request = HttpRequest.newBuilder().uri(new URI(kromerURL + "api/v1/transactions/create")).build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return true;
    }
}
