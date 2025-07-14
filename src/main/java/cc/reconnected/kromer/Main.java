package cc.reconnected.kromer;

import cc.reconnected.kromer.commands.BalanceCommand;
import cc.reconnected.kromer.commands.KromerCommand;
import cc.reconnected.kromer.commands.PayCommand;
import cc.reconnected.kromer.database.Database;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.responses.WalletCreateResponse;
import cc.reconnected.kromer.websockets.Websockets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import cc.reconnected.kromer.RccKromerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

public class Main implements DedicatedServerModInitializer {
    static Logger LOGGER = LoggerFactory.getLogger("rcc-kromer");
    public static Database database = new Database();

    public static RccKromerConfig config;
    public static HttpClient httpclient = HttpClient.newHttpClient();

    public static Websockets websockets = new Websockets();

    public void onInitializeServer() {
        ServerPlayConnectionEvents.JOIN.register(
                (a,b,c) -> {
                    firstLogin(a.player.getEntityName(), a.player.getUuid());
                }
        );

        CommandRegistrationCallback.EVENT.register(PayCommand::register);
        CommandRegistrationCallback.EVENT.register(BalanceCommand::register);
        CommandRegistrationCallback.EVENT.register(KromerCommand::register);

        config = RccKromerConfig.createAndLoad();
    }


    public static void firstLogin(String username, UUID uuid) {
        if(database.getWallet(uuid) != null) {
            return;
        }

        JsonObject playerObject = new JsonObject();
        playerObject.addProperty("name", username);
        playerObject.addProperty("uuid", uuid.toString());
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder().uri(new URI(config.KromerURL()+"api/_internal/wallet/create")).headers("Kromer-Key", config.KromerKey(), "Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(playerObject.toString())).build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        httpclient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
            errorHandler(response, throwable);
            WalletCreateResponse walletResponse = new Gson().fromJson(response.body(), WalletCreateResponse.class);

            database.setWallet(uuid, new Wallet(walletResponse.address, walletResponse.password));
            //generateMoney(uuid, 100);
        }).join();
    }
    /*
    // Suprisingly enough, kromer2 gives 100KOR by default.
    public static void generateMoney(UUID uuid, float amount) {
        CachedMetaData playerData = luckPerms.getUserManager().getUser(uuid).getCachedData().getMetaData();
        String walletAddress = playerData.getMetaValue("wallet_address");
        JsonObject moneyGenObject = new JsonObject();
        moneyGenObject.addProperty("address", walletAddress);
        moneyGenObject.addProperty("amount", amount);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder().uri(new URI(config.KromerURL() + "api/_internal/wallet/give-money")).POST(HttpRequest.BodyPublishers.ofString(moneyGenObject.toString())).build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        httpclient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete(Main::errorHandler).join();
    }*/

    public static void errorHandler(HttpResponse<String> response, Throwable throwable) {
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
        }
    }
}
