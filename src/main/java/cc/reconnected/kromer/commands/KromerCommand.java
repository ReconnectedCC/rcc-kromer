package cc.reconnected.kromer.commands;

import static cc.reconnected.kromer.Kromer.NETWORK_EXECUTOR;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.arguments.KromerArgumentType;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.database.WelfareData;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Objects;
import me.alexdevs.solstice.Solstice;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.addresses.GetAddress;
import ovh.sad.jkromer.http.internal.GiveMoney;
import ovh.sad.jkromer.http.misc.GetMotd;
import ovh.sad.jkromer.http.transactions.MakeTransaction;

import java.util.concurrent.CompletableFuture;


public class KromerCommand {

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment
    ) {
        var versionCommand = literal("version").executes(context -> {
            String modVersion = FabricLoader.getInstance()
                    .getModContainer("rcc-kromer")
                    .get()
                    .getMetadata()
                    .getVersion()
                    .getFriendlyString();

            CompletableFuture
                    .supplyAsync(() -> GetMotd.execute(), NETWORK_EXECUTOR)
                    .thenCompose(f -> f)
                    .whenComplete((b, ex) -> {
                        if (ex != null) {
                            context.getSource().getServer().execute(() ->
                                    context.getSource().sendSuccess(() -> Locale.use(Locale.Messages.ERROR, ex.getMessage()), false)
                            );
                            return;
                        }
                        if (b instanceof Result.Ok<GetMotd.GetMotdBody> ok) {
                            context.getSource().getServer().execute(() ->
                                    context.getSource().sendSuccess(
                                            () -> Locale.use(Locale.Messages.VERSION, modVersion, ok.value().motdPackage.version),
                                            false
                                    )
                            );
                        } else if (b instanceof Result.Err<GetMotd.GetMotdBody> err) {
                            context.getSource().getServer().execute(() ->
                                    context.getSource().sendSuccess(
                                            () -> Locale.use(Locale.Messages.ERROR, err.error()),
                                            false
                                    )
                            );
                        }
                    });
            return 1;
        });

        var infoCommand = literal("info").executes(context -> {
            Wallet wallet = Kromer.database.getWallet(context.getSource().getPlayer().getUUID());
            if (wallet == null) return 0;

            context.getSource().sendSuccess(() ->
                    Locale.use(Locale.Messages.KROMER_INFORMATION,
                            wallet.address, wallet.address, wallet.privatekey, wallet.privatekey), false
            );

            if (!Kromer.kromerStatus) {
                context.getSource().sendSuccess(Component::empty, false);
                context.getSource().sendSuccess(() -> Locale.use(Locale.Messages.KROMER_UNAVAILABLE), false);
                return 0;
            }
            return Command.SINGLE_SUCCESS;
        });

        var giveWalletCommand = literal("givewallet").then(
                argument("player", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                            Kromer.grantWallet(player.getScoreboardName(), player.getUUID(), player);
                            return Command.SINGLE_SUCCESS;
                        })
        );

        var setMoneyCommand = literal("addMoney").then(
                argument("player", EntityArgument.player()).then(
                        argument("amount", KromerArgumentType.kromerArg())
                                .executes(context -> {
                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                    var amount = KromerArgumentType.getBigDecimal(context, "amount");

                                    CompletableFuture
                                            .supplyAsync(() -> GiveMoney.execute(Kromer.config.internal_key, amount.floatValue(),
                                                    Kromer.database.getWallet(player.getUUID()).address), NETWORK_EXECUTOR)
                                            .thenCompose(f -> f)
                                            .whenComplete((b, ex) -> {
                                                if (ex != null) {
                                                    context.getSource().getServer().execute(() ->
                                                            context.getSource().sendSuccess(() -> Locale.use(Locale.Messages.ERROR, ex.getMessage()), false)
                                                    );
                                                    return;
                                                }
                                                if (b instanceof Result.Ok<GiveMoney.GiveMoneyResponse> ok) {
                                                    context.getSource().getServer().execute(() ->
                                                            context.getSource().sendSuccess(
                                                                    () -> Locale.use(Locale.Messages.ADDED_KRO, amount, player.getScoreboardName()),
                                                                    false
                                                            )
                                                    );
                                                } else if (b instanceof Result.Err<GiveMoney.GiveMoneyResponse> err) {
                                                    context.getSource().getServer().execute(() ->
                                                            context.getSource().sendSuccess(
                                                                    () -> Locale.use(Locale.Messages.ERROR, err.error()),
                                                                    false
                                                            )
                                                    );
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
            WelfareData welfareData = Solstice.playerData
                    .get(Objects.requireNonNull(context.getSource().getPlayer()).getUUID())
                    .getData(WelfareData.class);

            if (welfareData.welfareMuted) {
                context.getSource().sendSuccess(() -> Locale.use(Locale.Messages.WELFARE_NOT_MUTED), false);
            } else {
                context.getSource().sendSuccess(() -> Locale.use(Locale.Messages.WELFARE_MUTED), false);
            }
            welfareData.welfareMuted = !welfareData.welfareMuted;
            return Command.SINGLE_SUCCESS;
        });

        var optInOfWelfare = literal("optIn").executes(context -> {
            WelfareData welfareData = Solstice.playerData
                    .get(context.getSource().getPlayer().getUUID())
                    .getData(WelfareData.class);

            if (welfareData.optedOut) {
                welfareData.optedOut = false;
                context.getSource().sendSuccess(() -> Locale.use(Locale.Messages.OPTED_IN_WELFARE), false);
            } else {
                context.getSource().sendSuccess(() -> Locale.use(Locale.Messages.ALREADY_OPTED_IN), false);
            }
            return 1;
        });

        var optOutOfWelfare = literal("optOut")
                .executes(context -> {
                    WelfareData welfareData = Solstice.playerData
                            .get(context.getSource().getPlayer().getUUID())
                            .getData(WelfareData.class);

                    if (welfareData.optedOut) {
                        context.getSource().sendSuccess(() -> Locale.use(Locale.Messages.ALREADY_OPTED_OUT), false);
                        return 0;
                    }

                    context.getSource().sendSuccess(() -> Locale.use(Locale.Messages.OPTOUT_WARNING), false);
                    context.getSource().sendSuccess(Component::empty, false);
                    context.getSource().sendSuccess(() -> Locale.use(Locale.Messages.OPTOUT_INFO), false);
                    context.getSource().sendSuccess(Component::empty, false);
                    context.getSource().sendSuccess(() -> Locale.use(Locale.Messages.CONFIRM_OPTOUT_BUTTON), false);
                    return 1;
                })
                .then(literal("confirm").executes(context -> {
                    WelfareData welfareData = Solstice.playerData
                            .get(context.getSource().getPlayer().getUUID())
                            .getData(WelfareData.class);

                    welfareData.optedOut = true;

                    Wallet wallet = Kromer.database.getWallet(context.getSource().getPlayer().getUUID());
                    if (wallet == null) return 0;

                    CompletableFuture
                            .supplyAsync(() -> GetAddress.execute(wallet.address), NETWORK_EXECUTOR)
                            .thenCompose(f -> f)
                            .whenComplete((b, ex) -> {
                                if (ex != null) {
                                    context.getSource().getServer().execute(() ->
                                            context.getSource().sendSuccess(() -> Locale.use(Locale.Messages.ERROR, ex.getMessage()), false)
                                    );
                                    return;
                                }
                                if (b instanceof Result.Ok<GetAddress.GetAddressBody> ok) {
                                    CompletableFuture
                                            .supplyAsync(() -> MakeTransaction.execute(wallet.privatekey,
                                                    "serverwelf", ok.value().address.balance,
                                                    "[rcc-kromer] relinquishing all kromer"), NETWORK_EXECUTOR)
                                            .thenCompose(f2 -> f2)
                                            .whenComplete((z, ex2) -> {
                                                context.getSource().getServer().execute(() ->
                                                        context.getSource().sendSuccess(() -> Locale.use(Locale.Messages.OPTED_OUT_WELFARE), false)
                                                );
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
                .then(giveWalletCommand.requires(scs -> scs.hasPermission(4)))
                .then(setMoneyCommand.requires(scs -> scs.hasPermission(4)))
                .then(executeWelfare.requires(scs -> scs.hasPermission(4)));

        dispatcher.register(rootCommand);
    }
}
