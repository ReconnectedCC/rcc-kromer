/*
   When we get done, they gon' play this back
   I thought fine shit wanted me, but she wanted Max
   I can kick my feet up right now, I can really relax
   She gon' fuck the gang for free, we ain't gotta pull out stacks
   And my drip be out your league, I just put on black

   sad.ovh, MIT (C) 2025
*/

package cc.reconnected.kromer;

import cc.reconnected.kromer.arguments.AddressArgumentType;
import cc.reconnected.kromer.arguments.KromerArgumentInfo;
import cc.reconnected.kromer.arguments.KromerArgumentType;
import cc.reconnected.kromer.commands.BalanceCommand;
import cc.reconnected.kromer.commands.KromerCommand;
import cc.reconnected.kromer.commands.PayCommand;
import cc.reconnected.kromer.commands.TransactionsCommand;
import cc.reconnected.kromer.database.Database;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.database.WelfareData;
import cc.reconnected.kromer.networking.BalanceRequestPacket;
import cc.reconnected.kromer.networking.BalanceResponsePacket;
import cc.reconnected.kromer.networking.TransactionPacket;
import com.mojang.authlib.GameProfile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import com.typesafe.config.*;
import io.netty.buffer.Unpooled;
import me.alexdevs.solstice.Solstice;
import me.alexdevs.solstice.modules.afk.AfkModule;
import me.alexdevs.solstice.modules.afk.data.AfkPlayerData;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cc.reconnected.kromer.common.CommonMetaParser;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.addresses.GetAddress;
import ovh.sad.jkromer.http.internal.CreateWallet;
import ovh.sad.jkromer.http.internal.GiveMoney;
import ovh.sad.jkromer.http.misc.StartWs;
import ovh.sad.jkromer.jKromer;
import ovh.sad.jkromer.models.Transaction;

public class Kromer implements DedicatedServerModInitializer {
    public static Logger LOGGER = LoggerFactory.getLogger("rcc-kromer");
    public static Database database = new Database();
    public static final ExecutorService NETWORK_EXECUTOR = Executors.newCachedThreadPool();

    public static ConfigurationModel config;
    private static KromerWebsockets client;

    public static Boolean kromerStatus = false;
    public static int welfareQueued = 0;
    public static ConcurrentLRUCache<String, BigDecimal> balanceCache = new ConcurrentLRUCache<String, BigDecimal>(500); // 500 is arbitary

    private static Kromer instance = null;

    public static void connectWebsocket(MinecraftServer server)
        throws URISyntaxException {
        LOGGER.debug("Connecting to Websocket..");
        CompletableFuture
                .supplyAsync(StartWs::execute, NETWORK_EXECUTOR)
                .thenCompose(f -> f)
                .whenComplete((b, ex) -> {
                    if (b instanceof Result.Ok<StartWs.StartWsResponse> ok) {
                        LOGGER.debug("Websocket URL found: {}", ok.value().url);

                        try {
                            client = new KromerWebsockets(new URI(ok.value().url), server);
                        } catch (URISyntaxException e) {
                            throw new RuntimeException(e);
                        }

                        client.connect();

                        LOGGER.debug("Websocket connected.");
                    } else if (b instanceof Result.Err) {

                        LOGGER.debug("Websocket URL was not found. Retrying in 1 second.");

                        new Timer("WebSocket-Retry", true).schedule(
                                new TimerTask() {
                                    @Override
                                    public void run() {
                                        try {
                                            connectWebsocket(server);
                                        } catch (URISyntaxException ex2) {
                                            throw new RuntimeException(ex2);
                                        }
                                    }
                                },
                                1000
                        );
                    }
                });
    }

    public Optional<Kromer> instance() {
        return Optional.ofNullable(instance);
    }

    public void onInitializeServer() {
        instance = this;

        ArgumentTypeRegistry.registerArgumentType(new ResourceLocation("rcc-kromer", "kromer_amount"), KromerArgumentType.class, new KromerArgumentInfo());
        ArgumentTypeRegistry.registerArgumentType(new ResourceLocation("rcc-kromer","kromer_address"), AddressArgumentType.class, SingletonArgumentInfo.contextFree(AddressArgumentType::address));
        Flyway flyway = Flyway.configure()
                .dataSource("jdbc:sqlite:rcc-kromer.sqlite", null, null)
                .baselineOnMigrate(true)
                .load();

        flyway.migrate();

        Solstice.playerData.registerData(
            Solstice.ID.withPath("welfare"),
            WelfareData.class,
            WelfareData::new
        );
        ServerPlayNetworking.registerGlobalReceiver(BalanceRequestPacket.ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                Wallet wallet = database.getWallet(player.getUUID());
                if(wallet == null) {
                    LOGGER.error("BalanceRequestPacket: user " + player.getUUID().toString() + " has no valid wallet.");
                    return;
                }

                AtomicReference<BigDecimal> balance = new AtomicReference<>(balanceCache.get(wallet.address));
                if(balance.get() == null) {
                    CompletableFuture
                            .supplyAsync(() -> GetAddress.execute(wallet.address), NETWORK_EXECUTOR)
                            .thenCompose(future -> future)
                            .whenComplete((b, ex) -> {
                                if (ex != null) {
                                    LOGGER.error("BalanceRequestPacket: for user " + player.getUUID().toString() + " failed balance retrival due to " + ex.getMessage());
                                    return;
                                }

                                if (b instanceof Result.Ok<GetAddress.GetAddressBody> ok) {
                                    balance.set(ok.value().address.balance);
                                    balanceCache.put(wallet.address, ok.value().address.balance);
                                } else if (b instanceof Result.Err<GetAddress.GetAddressBody> err) {
                                    LOGGER.error("BalanceRequestPacket: for user " + player.getUUID().toString() + " failed balance retrival due to " + err.toString());
                                    return;
                                }
                            });
                }

                if(balance.get() == null) {
                    return;
                }

                ServerPlayNetworking.send(player, BalanceResponsePacket.ID, BalanceResponsePacket.serialise(balance.get()));
            });
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                connectWebsocket(server);
            } catch (URISyntaxException u) {
                u.printStackTrace();
            }
        });

        ServerPlayConnectionEvents.JOIN.register((a, b, c) -> {
            if (client == null) {
                LOGGER.error("Websocket client is null, cannot grant wallet nor calculate welfare.");
                return;
            }
            grantWallet(a.player.getScoreboardName(), a.player.getUUID(), a.player);
            checkTransfers(a.player);
            BigDecimal finalWelfare = calculateWelfare();
            executeWelfareForPlayer(a.player,finalWelfare);
        });

        CommandRegistrationCallback.EVENT.register(PayCommand::register);
        CommandRegistrationCallback.EVENT.register(BalanceCommand::register);
        CommandRegistrationCallback.EVENT.register(KromerCommand::register);
        CommandRegistrationCallback.EVENT.register(
            TransactionsCommand::register
        );

        config = ConfigBeanFactory.create(
                ConfigFactory.parseFile(
                        FabricLoader.getInstance().getConfigDir().resolve("rcc-kromer.hocon").toFile()
                ),
                ConfigurationModel.class
        );

        jKromer.endpoint_raw = config.getUrl();
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

    private static BigDecimal calculateWelfare(){
        if (client == null) {
            LOGGER.error("Websocket client is null, cannot calculate welfare.");
            return BigDecimal.valueOf(0);
        }
        List<ServerPlayer> playersWithSupporter = client.server
                .getPlayerList()
                .getPlayers()
                .stream()
                .filter(z -> Permissions.check(z, config.getSupporter().getGroup()) && !Permissions.check(z, config.getBot()))
                .toList();

        List<ServerPlayer> playersWithoutSupporter = client.server
                .getPlayerList()
                .getPlayers()
                .stream()
                .filter(z -> !Permissions.check(z, config.getSupporter().getGroup()) && !Permissions.check(z, config.getBot()))
                .toList();

        BigDecimal welfare = BigDecimal.valueOf(config.getWelfare());
        if (
                !playersWithSupporter.isEmpty() &&
                !playersWithoutSupporter.isEmpty()
        ) {
            welfare =
                    BigDecimal.valueOf(config.getWelfare())
                            .multiply(
                                    BigDecimal.valueOf(
                                            config.getSupporter().getMultiplier() * playersWithSupporter.size()
                                    )
                            );
        }
        return welfare;
    }

    public static void executeWelfare() {
        if (client == null) {
            LOGGER.error("Websocket client is null, cannot execute welfare.");
            return;
        }
        BigDecimal finalWelfare = calculateWelfare();

        client.server
            .getPlayerList()
            .getPlayers()
            .forEach(p -> {
               executeWelfareForPlayer(p,finalWelfare);
            });
    }
    private static void executeWelfareForPlayer(ServerPlayer player, BigDecimal baseWelfare) {
        if (baseWelfare.equals(BigDecimal.valueOf(0))) {
            return;
        }
        if(config.getBot() != null) {
            if(Permissions.check(player, config.getBot())) return; // If bot then bye
        }
        Wallet wallet = Kromer.database.getWallet(player.getUUID());
        if (wallet == null) return;


        WelfareData welfareData = Solstice.playerData
                .get(player.getUUID())
                .getData(WelfareData.class);
        int interval = 3600; // one hour, in seconds, since rcc-kromer does hourly welfare.
        AfkPlayerData solsticeData = Solstice.modules
                .getModule(AfkModule.class)
                .get()
                .getPlayerData(player.getUUID());
        int activeTime = solsticeData.activeTime;
        var oldActiveTime = welfareData.oldActiveTime;
        float deltaActiveTime = activeTime - oldActiveTime;
        BigDecimal relativeDeltaActiveTime = BigDecimal.valueOf(deltaActiveTime / interval)
                .setScale(2, RoundingMode.HALF_EVEN); // make it 1 if equal to interval, or greater if they log off and rejoin mid-interval
        BigDecimal finalWelfare = baseWelfare.multiply(relativeDeltaActiveTime)
                .setScale(2, RoundingMode.HALF_EVEN);
        if (finalWelfare.equals(BigDecimal.valueOf(0))) return;
        if (
                !(welfareData.welfareMuted || welfareData.optedOut)
        ) {
            player.sendSystemMessage(
                    Locale.use(Locale.Messages.WELFARE_GIVEN, finalWelfare)
            );
        }
        if(!welfareData.optedOut) {
            CompletableFuture
                    .supplyAsync(() -> GiveMoney.execute(config.getInternal_key(), finalWelfare, wallet.address), NETWORK_EXECUTOR)
                    .thenCompose(f -> f);
        }
        welfareData.oldActiveTime = activeTime;
    }
    public static String getNameFromWallet(String address) {
        String userName = address; // if from is
        Tuple<UUID, Wallet> fromWallet = database.getWallet(userName);

        if (fromWallet != null) {
            Optional<GameProfile> gf = Objects.requireNonNull(
                client.server.getProfileCache()
            ).get(fromWallet.getA());
            if (gf.isPresent()) {
                userName = gf.get().getName();
            }
        }

        boolean found = !Objects.equals(userName, address);

        return found ? String.format("%s (%s)", userName, address) : address;
    }

    public static void notifyTransfer(
        ServerPlayer player,
        Transaction transaction
    ) {
        BigDecimal balVal = Kromer.balanceCache.get(transaction.to);
        if(balanceCache == null) {
            balVal = BigDecimal.valueOf(-1f);
        } else {
            balVal = balVal.add(transaction.value);
            Kromer.balanceCache.put(transaction.to, balVal);
        }

        ServerPlayNetworking.send(player, TransactionPacket.ID, TransactionPacket.serialise(transaction, balVal));

        var result = CommonMetaParser.parseWithResult(transaction.metadata);

        if(result.success) {
            if(result.pairs.containsKey("message")) {
                player.sendSystemMessage(
                        Locale.useSafe( // use useSafe, removes all <click's and whatnot.
                                Locale.Messages.NOTIFY_TRANSFER_MESSAGE,
                                transaction.value,
                                getNameFromWallet(transaction.from),
                                result.pairs.get("message")
                        )
                );
            } else { // Don't duplicate code here. However, I don't want to make an extra function, so be it.
                player.sendSystemMessage(
                        Locale.use(
                                Locale.Messages.NOTIFY_TRANSFER,
                                transaction.value,
                                getNameFromWallet(transaction.from)
                        )
                );
            }
        } else {
            player.sendSystemMessage(
                    Locale.use(
                            Locale.Messages.NOTIFY_TRANSFER,
                            transaction.value,
                            getNameFromWallet(transaction.from)
                    )
            );
        }

    }

    public static void checkTransfers(ServerPlayer player) {
        Wallet wallet = database.getWallet(player.getUUID());

        if (wallet == null) return;

        if (wallet.outgoingNotSeen.length != 0) {
            for (int i = 0; i < wallet.outgoingNotSeen.length; i++) {
                Transaction transaction = wallet.outgoingNotSeen[i];

                player.sendSystemMessage(
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
                player.sendSystemMessage(
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
        database.setWallet(player.getUUID(), wallet);
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
        ServerPlayer player
    ) {
        AfkPlayerData solsticeData = Solstice.modules
                .getModule(AfkModule.class)
                .get()
                .getPlayerData(uuid);
        WelfareData welfareData = Solstice.playerData
                .get(player.getUUID())
                .getData(WelfareData.class);
        if (welfareData.oldActiveTime == 0) {
            welfareData.oldActiveTime = solsticeData.activeTime; // Prevent double retroactive kromer.
        }
        if (database.getWallet(uuid) != null) {
            return;
        }

        // Retroactive KRO giving
        float kroAmountRaw = (float) (((double) solsticeData.activeTime /
                3600) *
                config.getWelfare());

        if (kroAmountRaw != 0) {
            player.sendSystemMessage(
                Locale.use(Locale.Messages.RETROACTIVE, kroAmountRaw)
            );
        }

        CompletableFuture
                .supplyAsync(() -> CreateWallet.execute(config.getInternal_key(), name, uuid.toString()), NETWORK_EXECUTOR)
                .thenCompose(f -> f)
                .whenComplete((createWalletResult, ex) -> {
                    if (ex != null) {
                        LOGGER.error("Failed to create wallet for user " + name, ex);
                        return;
                    }
                    if (createWalletResult instanceof Result.Ok<CreateWallet.CreateWalletResponse> createWallet) {
                        Transaction[] array = {};
                        Wallet wallet = new Wallet(
                                createWallet.value().address,
                                createWallet.value().privatekey,
                                array,
                                array
                        );
                        database.setWallet(uuid, wallet);

                        if (kroAmountRaw != 0) {
                            CompletableFuture
                                    .supplyAsync(() -> GiveMoney.execute(config.getInternal_key(), BigDecimal.valueOf(kroAmountRaw), createWallet.value().address), NETWORK_EXECUTOR)
                                    .thenCompose(f -> f)
                                    .whenComplete((giveMoneyResult, ex2) -> {
                                        if (ex2 != null) {
                                            LOGGER.error("Failed to give retroactive kro to " + name, ex2);
                                        }

                                    });
                        }
                    } else if (createWalletResult instanceof Result.Err<CreateWallet.CreateWalletResponse> err) {
                        LOGGER.warn("Was not able to give user " + name + " their wallet. " + err.error().error() + ", param: " + err.error().parameter());
                    }
                });

    }
}
