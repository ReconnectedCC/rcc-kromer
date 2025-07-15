package cc.reconnected.kromer;

import cc.reconnected.kromer.commands.BalanceCommand;
import cc.reconnected.kromer.commands.KromerCommand;
import cc.reconnected.kromer.commands.PayCommand;
import cc.reconnected.kromer.database.Database;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.responses.Transaction;
import cc.reconnected.kromer.responses.TransactionCreateResponse;
import cc.reconnected.kromer.responses.WalletCreateResponse;
import cc.reconnected.kromer.responses.WebsocketStartResponse;
import cc.reconnected.kromer.responses.errors.GenericError;
import cc.reconnected.kromer.websockets.KromerClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import me.alexdevs.solstice.Solstice;
import me.alexdevs.solstice.data.PlayerData;
import me.alexdevs.solstice.modules.afk.AfkModule;
import me.alexdevs.solstice.modules.afk.commands.ActiveTimeCommand;
import me.alexdevs.solstice.modules.afk.data.AfkPlayerData;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import cc.reconnected.kromer.RccKromerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import org.java_websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Format;
import java.util.*;

public class Main implements DedicatedServerModInitializer {
    static Logger LOGGER = LoggerFactory.getLogger("rcc-kromer");
    public static Database database = new Database();

    public static RccKromerConfig config;
    public static HttpClient httpclient = HttpClient.newHttpClient();
    private static KromerClient client;
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            try {
                System.out.println("Requesting websocket");

                HttpRequest request = HttpRequest.newBuilder().uri(
                        new URI(Main.config.KromerURL()+"api/krist/ws/start"))
                            .headers("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString((new JsonObject()).toString()))
                            .build();

                Main.httpclient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
                    if(errorHandler(response, throwable)) return;

                    WebsocketStartResponse resp = new Gson().fromJson(response.body(), WebsocketStartResponse.class);
                    System.out.println("Websocket found, URL: " + resp.url);

                    try {
                         client = new KromerClient( new URI(resp.url), server );
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }

                    client.connect();

                    System.out.println("Websocket started. Connecting to domain: " + client.getURI().toString());
                }).join();
            } catch (java.net.URISyntaxException u) {
                u.printStackTrace();
            }
        });

        ServerPlayConnectionEvents.JOIN.register(
                (a,b,c) -> {
                    firstLogin(a.player.getEntityName(), a.player.getUuid(), a.player);
                    checkTransfers(a.player);
                }
        );

        CommandRegistrationCallback.EVENT.register(PayCommand::register);
        CommandRegistrationCallback.EVENT.register(BalanceCommand::register);
        CommandRegistrationCallback.EVENT.register(KromerCommand::register);

        config = RccKromerConfig.createAndLoad();
    }

    private static String getNameFromWallet(String address) {
        String userName = address; // if from is
        Pair<UUID, Wallet> fromWallet = database.getWallet(userName);

        if(fromWallet != null) {
            Optional<GameProfile> gf = client.server.getUserCache().getByUuid(fromWallet.getLeft());
            if(gf.isPresent()) {
                userName = gf.get().getName();
            }
        }

        Boolean found = !Objects.equals(userName, address);

        return found ? String.format("%s (%s)", userName, address) : address;
    }

    public static void notifyTransfer(ServerPlayerEntity player, Transaction transaction) {
        player.sendMessage(
                Text.literal("You have been sent ").formatted(Formatting.GREEN)
                        .append(Text.literal(String.valueOf(transaction.value)).formatted(Formatting.DARK_GREEN))
                        .append(Text.literal("KRO, from ").formatted(Formatting.GREEN))
                        .append(Text.literal((getNameFromWallet(transaction.from)) + "!").formatted(Formatting.DARK_GREEN))
        );
    }
    public static void checkTransfers(ServerPlayerEntity player) {
        Wallet wallet = database.getWallet(player.getUuid());

        if(wallet == null) return;
        System.out.println("checktransfers");
        System.out.println(wallet.toString());

        if(wallet.outgoingNotSeen.length != 0) {
            for (int i = 0; i < wallet.outgoingNotSeen.length; i++) {
                Transaction transaction = wallet.outgoingNotSeen[i];

                player.sendMessage(
                        Text.literal("From your account, ").formatted(Formatting.RED)
                                .append(Text.literal(String.valueOf(transaction.value)).formatted(Formatting.DARK_RED))
                                .append(Text.literal("KRO, has been sent to").formatted(Formatting.RED))
                                .append(Text.literal(getNameFromWallet(transaction.to) + ". ").formatted(Formatting.DARK_RED))
                                .append(Text.literal("Executed at: " + transaction.time.toString()).formatted(Formatting.RED))

                );
            }

        }

        if(wallet.incomingNotSeen.length != 0) {
            for (int i = 0; i < wallet.incomingNotSeen.length; i++) {
                Transaction transaction = wallet.incomingNotSeen[i];
                player.sendMessage(
                        Text.literal(getNameFromWallet(transaction.from)).formatted(Formatting.DARK_GREEN)
                                .append(Text.literal(" deposited ").formatted(Formatting.GREEN))
                                .append(Text.literal((transaction.value)+"KRO").formatted(Formatting.DARK_GREEN))
                                .append(Text.literal(" into your account. ").formatted(Formatting.GREEN))
                                .append(Text.literal("Executed at: " + transaction.time.toString()).formatted(Formatting.GREEN))
                );
            }
        }

        wallet.incomingNotSeen = new Transaction[]{};
        wallet.outgoingNotSeen = new Transaction[]{};
        database.setWallet(player.getUuid(), wallet);
    }
    public static void firstLogin(String name, UUID uuid, ServerPlayerEntity player) {
        if(database.getWallet(uuid) != null) {
            return;
        }

        // Retroactive KRO giving
        AfkPlayerData solsticeData = Solstice.modules.getModule(AfkModule.class).getPlayerData(uuid);
        float kroAmount = (float) (((double) solsticeData.activeTime / 3600) * 1.50);
        if(kroAmount != 0 && player != null) {
            player.sendMessage(
                    Text.literal("You have recieved: ").formatted(Formatting.DARK_GREEN)
                            .append(Text.literal((kroAmount)+"KRO").formatted(Formatting.DARK_GREEN))
                            .append(Text.literal(" for your playtime!").formatted(Formatting.GREEN))
            );
        }

        JsonObject playerObject = new JsonObject();
        playerObject.addProperty("name", name);
        playerObject.addProperty("uuid", uuid.toString());
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder().uri(new URI(config.KromerURL()+"api/_internal/wallet/create")).headers("Kromer-Key", config.KromerKey(), "Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(playerObject.toString())).build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        httpclient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
            if(errorHandler(response, throwable)) return;
            WalletCreateResponse walletResponse = new Gson().fromJson(response.body(), WalletCreateResponse.class);
            Transaction[] array = {};
            Wallet wallet = new Wallet(walletResponse.address, walletResponse.password, array, array);
            database.setWallet(uuid, wallet);
            if(kroAmount != 0) {
                Main.giveMoney(wallet, kroAmount);
            }
        }).join();
    }
    public static void giveMoney(Wallet wallet, float amount) {
        JsonObject giveMoneyObject = new JsonObject();
        giveMoneyObject.addProperty("address", wallet.address);
        giveMoneyObject.addProperty("amount", amount);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder().uri(new URI(config.KromerURL()+"api/_internal/wallet/give-money")).headers("Kromer-Key", config.KromerKey(), "Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(giveMoneyObject.toString())).build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        httpclient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
            if(errorHandler(response, throwable)) return;
        }).join();
    }
    public static Boolean errorHandler(HttpResponse<String> response, Throwable throwable) {
        if (throwable != null) {
            LOGGER.error("Failed to send player data to Kromer", throwable);
            return true;
        }
        if (response.statusCode() != 200) {
            LOGGER.error("Failed to send player data to Kromer: " + response.body());
            return true;
        }
        if (response.body() == null) {
            LOGGER.error("Failed to send player data to Kromer: No response body");
            return true;

        }

        return false;
    }
}
