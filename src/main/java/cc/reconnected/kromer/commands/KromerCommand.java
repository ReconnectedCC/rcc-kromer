package cc.reconnected.kromer.commands;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.arguments.KromerArgumentType;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.database.WelfareData;
import cc.reconnected.kromer.networking.BalanceResponsePacket;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.alexdevs.solstice.Solstice;
import me.alexdevs.solstice.api.command.LocalGameProfile;
import me.alexdevs.solstice.api.text.Components;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.addresses.GetAddress;
import ovh.sad.jkromer.http.internal.GiveMoney;
import ovh.sad.jkromer.http.misc.GetMotd;
import ovh.sad.jkromer.http.transactions.MakeTransaction;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cc.reconnected.kromer.Kromer.NETWORK_EXECUTOR;
import static cc.reconnected.kromer.Kromer.balanceCache;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;


public class KromerCommand {

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment
    ) {
        var versionCommand = literal("version").executes(context -> {
            String modVersion = FabricLoader.getInstance()
                    .getModContainer("rcc-kromer")
                    .orElseThrow()
                    .getMetadata()
                    .getVersion()
                    .getFriendlyString();

            CompletableFuture
                    .supplyAsync(GetMotd::execute, NETWORK_EXECUTOR)
                    .thenCompose(f -> f)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            context.getSource().sendFailure(Locale.error(ex.getMessage()));
                            return;
                        }

                        if (result instanceof Result.Ok<GetMotd.GetMotdBody> ok) {
                            context.getSource().sendSuccess(() -> Locale.parse(Locale.Messages.VERSION, Map.of(
                                    "pluginVersion", Component.literal(modVersion),
                                    "kromerVersion", Component.literal(ok.value().motdPackage.version)
                            )), false);

                        } else if (result instanceof Result.Err<GetMotd.GetMotdBody> err) {
                            context.getSource().sendFailure(Locale.error(err.error().toString()));
                        }
                    });
            return 1;
        });

        var infoCommand = literal("info").executes(context -> {
            var player = context.getSource().getPlayerOrException();
            Wallet wallet = Kromer.database.getWallet(player.getUUID());

            if (wallet == null)
                return 0;

            context.getSource().sendSuccess(() ->
                    Locale.parse(Locale.Messages.KROMER_INFORMATION,
                            Map.of(
                                    "address", Component.literal(wallet.address),
                                    "privateKeyButton", Locale.buttonCopy("Copy key", "Click to copy the private key", wallet.privatekey)
                            )
                    ), false
            );

            if (!Kromer.kromerStatus) {
                context.getSource().sendFailure(Locale.parse(Locale.Messages.KROMER_UNAVAILABLE));
                return 0;
            }

            return Command.SINGLE_SUCCESS;
        });

        var giveWalletCommand = literal("givewallet")
                .then(argument("player", StringArgumentType.word())
                        .suggests(LocalGameProfile::suggest)
                        .executes(context -> {
                            var profile = LocalGameProfile.getProfile(context, "player");

                            Kromer.grantWallet(profile);

                            context.getSource().sendSuccess(() -> Component.literal("Granted wallet to " + profile.getName()), true);
                            return Command.SINGLE_SUCCESS;
                        })
                );

        var addMoneyCommand = literal("addMoney")
                .then(argument("player", StringArgumentType.word())
                        .suggests(LocalGameProfile::suggest)
                        .then(argument("amount", KromerArgumentType.kromerArg(true, false))
                                .executes(context -> {
                                    GameProfile profile = LocalGameProfile.getProfile(context, "player");
                                    BigDecimal amount = KromerArgumentType.getBigDecimal(context, "amount");

                                    ServerPlayer player = context.getSource().getServer().getPlayerList().getPlayer(profile.getId());
                                    CommandSourceStack source = context.getSource();

                                    CompletableFuture
                                            .supplyAsync(() -> GiveMoney.execute(Kromer.config.getInternal_key(), amount,
                                                    Kromer.database.getWallet(profile.getId()).address), NETWORK_EXECUTOR)
                                            .thenCompose(f -> f)
                                            .whenComplete((result, ex) -> {
                                                if (ex != null) {
                                                    source.sendFailure(Locale.error(ex.getMessage()));
                                                    return;
                                                }

                                                if (result instanceof Result.Ok<GiveMoney.GiveMoneyResponse> response) {
                                                    balanceCache.put(response.value().wallet.address, response.value().wallet.balance);
                                                    if (player != null) {
                                                        ServerPlayNetworking.send(player, BalanceResponsePacket.ID, BalanceResponsePacket.serialise(response.value().wallet.balance));
                                                    }

                                                    source.sendSuccess(() -> Locale.parse(Locale.Messages.ADDED_KRO, amount,
                                                            Map.of(
                                                                    "player", Component.literal(profile.getName())
                                                            )
                                                    ), true);
                                                } else if (result instanceof Result.Err<GiveMoney.GiveMoneyResponse> error) {
                                                    source.sendFailure(Locale.error(error.error().toString()));
                                                }
                                            });
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                );

        var executeWelfare = literal("welfare").executes(context -> {
            Kromer.executeWelfare();
            return Command.SINGLE_SUCCESS;
        });

        var muteWelfare = literal("muteWelfare").executes(context -> {
            var player = context.getSource().getPlayerOrException();

            WelfareData welfareData = Solstice.playerData
                    .get(player.getUUID())
                    .getData(WelfareData.class);

            welfareData.welfareMuted = !welfareData.welfareMuted;

            if (welfareData.welfareMuted) {
                context.getSource().sendSuccess(() -> Locale.parse(Locale.Messages.WELFARE_NOT_MUTED), false);
            } else {
                context.getSource().sendSuccess(() -> Locale.parse(Locale.Messages.WELFARE_MUTED), false);
            }

            return Command.SINGLE_SUCCESS;
        });

        var optInOfWelfare = literal("optIn").executes(context -> {
            var player = context.getSource().getPlayerOrException();
            WelfareData welfareData = Solstice.playerData.get(player.getUUID()).getData(WelfareData.class);

            if (welfareData.optedOut) {
                welfareData.optedOut = false;
                context.getSource().sendSuccess(() -> Locale.parse(Locale.Messages.OPTED_IN_WELFARE), false);
            } else {
                context.getSource().sendSuccess(() -> Locale.parse(Locale.Messages.ALREADY_OPTED_IN), false);
            }

            return 1;
        });

        var optOutOfWelfare = literal("optOut")
                .executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    WelfareData welfareData = Solstice.playerData.get(player.getUUID()).getData(WelfareData.class);

                    if (welfareData.optedOut) {
                        context.getSource().sendSuccess(() -> Locale.parse(Locale.Messages.ALREADY_OPTED_OUT), false);
                        return 0;
                    }

                    context.getSource().sendSuccess(() -> Locale.parse(Locale.Messages.OPTOUT_WARNING, Map.of(
                            "confirmButton", Components.button("Confirm", "Click to opt out of welfare", "/kromer optOut confirm")
                    )), false);
                    return 1;
                })
                .then(literal("confirm").executes(context -> {
                    final var source = context.getSource();
                    var player = source.getPlayerOrException();
                    WelfareData welfareData = Solstice.playerData.get(player.getUUID()).getData(WelfareData.class);

                    welfareData.optedOut = true;

                    Wallet wallet = Kromer.database.getWallet(player.getUUID());
                    if (wallet == null) return 0;

                    CompletableFuture
                            .supplyAsync(() -> GetAddress.execute(wallet.address), NETWORK_EXECUTOR)
                            .thenCompose(f -> f)
                            .whenComplete((addressResponse, ex) -> {
                                if (ex != null) {
                                    source.sendFailure(Locale.error(ex.getMessage()));
                                    return;
                                }
                                if (addressResponse instanceof Result.Ok<GetAddress.GetAddressBody> ok) {
                                    CompletableFuture
                                            .supplyAsync(() -> MakeTransaction.execute(wallet.privatekey,
                                                    "serverwelf", ok.value().address.balance,
                                                    "[rcc-kromer] relinquishing all kromer"), NETWORK_EXECUTOR)
                                            .thenCompose(f2 -> f2)
                                            .whenComplete((response, ex2) -> {
                                                source.sendSuccess(() -> Locale.parse(Locale.Messages.OPTED_OUT_WELFARE), false);
                                            });
                                }
                            });
                    return 1;
                }));

        var rootCommand = literal("kromer")
                .then(versionCommand)
                .then(infoCommand)
                .then(muteWelfare)
                .then(optInOfWelfare)
                .then(optOutOfWelfare)
                .then(giveWalletCommand.requires(Permissions.require("rcc-kromer.give-wallet", 4)))
                .then(addMoneyCommand.requires(Permissions.require("rcc-kromer.add-money", 4)))
                .then(executeWelfare.requires(Permissions.require("rcc-kromer.execute-welfare", 4)));

        dispatcher.register(rootCommand);
    }
}
