// hi

package cc.reconnected.kromer;

import cc.reconnected.kromer.commands.BalanceCommand;
import cc.reconnected.kromer.commands.KromerCommand;
import cc.reconnected.kromer.commands.PayCommand;
import cc.reconnected.kromer.commands.TransactionsCommand;
import cc.reconnected.kromer.database.Database;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.database.WelfareData;
import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import me.alexdevs.solstice.Solstice;
import me.alexdevs.solstice.modules.afk.AfkModule;
import me.alexdevs.solstice.modules.afk.data.AfkPlayerData;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Pair;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ovh.sad.jkromer.Errors;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.internal.CreateWallet;
import ovh.sad.jkromer.http.internal.GiveMoney;
import ovh.sad.jkromer.http.misc.StartWs;
import ovh.sad.jkromer.jKromer;
import ovh.sad.jkromer.models.Transaction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Kromer implements DedicatedServerModInitializer {

    public static Logger LOGGER = LoggerFactory.getLogger("rcc-kromer");
    public static Database database = new Database();

    public static cc.reconnected.kromer.RccKromerConfig config;
    private static KromerWebsockets client;

    public static Boolean kromerStatus = false;
    public static int welfareQueued = 0;

    public static void connectWebsocket(MinecraftServer server)
        throws URISyntaxException {
        LOGGER.debug("Connecting to Websocket..");

        StartWs.execute()
                .whenComplete((b, ex) -> {
                    switch (b) {
                        case Result.Ok<StartWs.StartWsResponse> ok -> {
                            LOGGER.debug("Websocket URL found: {}", ok.value().url);

                            try {
                                client = new KromerWebsockets(new URI(ok.value().url), server);
                            } catch (URISyntaxException e) {
                                throw new RuntimeException(e);
                            }

                            client.connect();

                            LOGGER.debug("Websocket connected.");
                        }
                        case Result.Err<StartWs.StartWsResponse> err -> {
                            LOGGER.debug(
                                    "Websocket URL was not found. Retrying in 1 second."
                            );

                            new Timer("WebSocket-Retry", true).schedule(
                                    new TimerTask() {
                                        @Override
                                        public void run() {
                                            try {
                                                connectWebsocket(server);
                                            } catch (URISyntaxException ex) {
                                                throw new RuntimeException(ex);
                                            }
                                        }
                                        },
                                    1000
                            );
                        }
                    }
                });
    }

    public void onInitializeServer() {

        Flyway flyway = Flyway.configure()
                .dataSource("jdbc:sqlite:rcc-kromer.sqlite", null, null)
                .baselineOnMigrate(true)
                .load();

        flyway.migrate();

        Solstice.playerData.registerData(
            "welfare",
            WelfareData.class,
            WelfareData::new
        );

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                connectWebsocket(server);
            } catch (java.net.URISyntaxException u) {
                u.printStackTrace();
            }
        });

        ServerPlayConnectionEvents.JOIN.register((a, b, c) -> {
            grantWallet(a.player.getEntityName(), a.player.getUuid(), a.player);
            checkTransfers(a.player);
        });

        CommandRegistrationCallback.EVENT.register(PayCommand::register);
        CommandRegistrationCallback.EVENT.register(BalanceCommand::register);
        CommandRegistrationCallback.EVENT.register(KromerCommand::register);
        CommandRegistrationCallback.EVENT.register(
            TransactionsCommand::register
        );

        config = cc.reconnected.kromer.RccKromerConfig.createAndLoad();
        jKromer.endpoint_raw = config.KromerURL();
        jKromer.endpoint = jKromer.endpoint_raw + "/api/krist";

        ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

        long initialDelay = getDelayUntilNextHourInSeconds();
        long oneHour = 3600; // seconds
        scheduler.scheduleAtFixedRate(
            () -> {
                if (!Kromer.kromerStatus) {
                    welfareQueued++;
                    return;
                }
                Kromer.executeWelfare();
            },
            initialDelay,
            oneHour,
            TimeUnit.SECONDS
        );
    }

    public static void executeWelfare() {
        List<ServerPlayerEntity> playersWithSupporter = client.server
            .getPlayerManager()
            .getPlayerList()
            .stream()
            .filter(z -> Permissions.check(z, config.SupporterGroup()))
            .toList();

        List<ServerPlayerEntity> playersWithoutSupporter = client.server
            .getPlayerManager()
            .getPlayerList()
            .stream()
            .filter(z -> !Permissions.check(z, config.SupporterGroup()))
            .toList();

        float welfare = config.HourlyWelfare();
        if (
            !playersWithSupporter.isEmpty()
        ) {
            welfare =
                config.HourlyWelfare() *
                (config.SupporterMultiplier() * playersWithSupporter.size());
        }

        float finalWelfare = Math.round(welfare * 100f) / 100f;

        client.server
            .getPlayerManager()
            .getPlayerList()
            .forEach(p -> {
                Wallet wallet = Kromer.database.getWallet(p.getUuid());
                if (wallet == null) return;

                if (
                    Solstice.modules.getModule(AfkModule.class).isPlayerAfk(p)
                ) return;
                WelfareData welfareData = Solstice.playerData
                        .get(p.getUuid())
                        .getData(WelfareData.class);
                if (
                    !(welfareData.welfareMuted || welfareData.optedOut)
                ) {
                    p.sendMessage(
                        Locale.use(Locale.Messages.WELFARE_GIVEN, finalWelfare)
                    );
                }
                if(!welfareData.optedOut) {
                    GiveMoney.execute(config.KromerKey(), finalWelfare, wallet.address).join();
                }
            });
    }

    public static String getNameFromWallet(String address) {
        String userName = address; // if from is
        Pair<UUID, Wallet> fromWallet = database.getWallet(userName);

        if (fromWallet != null) {
            Optional<GameProfile> gf = Objects.requireNonNull(
                client.server.getUserCache()
            ).getByUuid(fromWallet.getLeft());
            if (gf.isPresent()) {
                userName = gf.get().getName();
            }
        }

        boolean found = !Objects.equals(userName, address);

        return found ? String.format("%s (%s)", userName, address) : address;
    }

    public static void notifyTransfer(
        ServerPlayerEntity player,
        Transaction transaction
    ) {
        player.sendMessage(
            Locale.use(
                Locale.Messages.NOTIFY_TRANSFER,
                transaction.value,
                getNameFromWallet(transaction.from)
            )
        );
    }

    public static void checkTransfers(ServerPlayerEntity player) {
        Wallet wallet = database.getWallet(player.getUuid());

        if (wallet == null) return;

        if (wallet.outgoingNotSeen.length != 0) {
            for (int i = 0; i < wallet.outgoingNotSeen.length; i++) {
                Transaction transaction = wallet.outgoingNotSeen[i];

                player.sendMessage(
                    Locale.use(
                        Locale.Messages.OUTGOING_NOT_SEEN,
                        transaction.value,
                        getNameFromWallet(transaction.to),
                        transaction.time.toString()
                    )
                );
            }
        }

        if (wallet.incomingNotSeen.length != 0) {
            for (int i = 0; i < wallet.incomingNotSeen.length; i++) {
                Transaction transaction = wallet.incomingNotSeen[i];
                player.sendMessage(
                    Locale.use(
                        Locale.Messages.INCOMING_NOT_SEEN,
                        getNameFromWallet(transaction.from),
                        transaction.value,
                        transaction.time.toString()
                    )
                );
            }
        }

        wallet.incomingNotSeen = new Transaction[] {};
        wallet.outgoingNotSeen = new Transaction[] {};
        database.setWallet(player.getUuid(), wallet);
    }

    private static long getDelayUntilNextHourInSeconds() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextHour = now
            .plusHours(1)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);
        Duration duration = Duration.between(now, nextHour);
        return duration.getSeconds();
    }

    public static void grantWallet(
        String name,
        UUID uuid,
        ServerPlayerEntity player
    ) {
        if (database.getWallet(uuid) != null) {
            return;
        }

        // Retroactive KRO giving
        AfkPlayerData solsticeData = Solstice.modules
            .getModule(AfkModule.class)
            .getPlayerData(uuid);
        float kroAmountRaw = (float) (((double) solsticeData.activeTime /
                3600) *
                config.HourlyWelfare());
        float kroAmount = Math.round(kroAmountRaw * 100f) / 100f;

        if (kroAmount != 0 && player != null) {
            player.sendMessage(
                Locale.use(Locale.Messages.RETROACTIVE, kroAmount)
            );
        }

        var createWalletResult = CreateWallet.execute(config.KromerKey(), name, UUID.randomUUID().toString()).join();


        if (createWalletResult instanceof Result.Ok(CreateWallet.CreateWalletResponse createWallet)) {
            Transaction[] array = {};
            Wallet wallet = new Wallet(
                    createWallet.address,
                    createWallet.privatekey,
                    array,
                    array
            );
            database.setWallet(uuid, wallet);

            if(kroAmount != 0) {
                GiveMoney.execute(config.KromerKey(), kroAmount, createWallet.address).join();
            }
        } else if (createWalletResult instanceof Result.Err(Errors.ErrorResponse err)) {
            System.out.println("Was not able to give user " + name + " their wallet. " + err.error() + ", param: " + err.parameter());
        }

    }
}
