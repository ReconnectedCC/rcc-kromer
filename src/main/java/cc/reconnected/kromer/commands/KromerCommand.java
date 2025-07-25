package cc.reconnected.kromer.commands;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.database.WelfareData;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.Objects;
import me.alexdevs.solstice.Solstice;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.addresses.GetAddress;
import ovh.sad.jkromer.http.internal.GiveMoney;
import ovh.sad.jkromer.http.misc.GetMotd;
import ovh.sad.jkromer.http.transactions.MakeTransaction;

public class KromerCommand {

    public static void register(
        CommandDispatcher<ServerCommandSource> dispatcher,
        CommandRegistryAccess registryAccess,
        CommandManager.RegistrationEnvironment environment
    ) {
        var versionCommand = literal("version").executes(context -> {
                String modVersion = FabricLoader.getInstance()
                    .getModContainer("rcc-kromer")
                    .get()
                    .getMetadata()
                    .getVersion()
                    .getFriendlyString(); // WHY

                GetMotd.execute().whenComplete((b, ex) -> {
                    switch (b) {
                        case Result.Ok<GetMotd.GetMotdBody> ok -> context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.VERSION, modVersion,
                                ok.value().motdPackage.version), false);
                        case Result.Err<GetMotd.GetMotdBody> err -> context.getSource()
                                .sendFeedback(() ->
                                                Locale.use(Locale.Messages.ERROR, "Error: " + err.error() + " param: " + err.error().parameter())
                                        , false);
                    }
                }).join();
                return 1;
            });

        var infoCommand = literal("info").executes(context -> {
                Wallet wallet = Kromer.database.getWallet(
                    context.getSource().getPlayer().getUuid()
                );
                if (wallet == null) return 0;

                context
                    .getSource()
                    .sendFeedback(
                        () ->
                            Locale.use(
                                Locale.Messages.KROMER_INFORMATION,
                                wallet.address,
                                wallet.address,
                                wallet.privatekey,
                                wallet.privatekey
                            ),
                        false
                    );
                if (!Kromer.kromerStatus) {
                    context.getSource().sendFeedback(Text::empty, false);
                    context
                        .getSource()
                        .sendFeedback(
                            () ->
                                Locale.use(Locale.Messages.KROMER_UNAVAILABLE),
                            false
                        );
                    return 0;
                }
                return Command.SINGLE_SUCCESS;
            });

        var giveWalletCommand = literal("givewallet").then(
                argument("player", EntityArgumentType.player())
                    .executes(context -> {
                        ServerPlayerEntity player =
                            EntityArgumentType.getPlayer(context, "player");

                        Kromer.grantWallet(
                            player.getEntityName(),
                            player.getUuid(),
                            player
                        );
                        return Command.SINGLE_SUCCESS;
                    })
            );

        var setMoneyCommand = literal("addMoney").then(
                argument("player", EntityArgumentType.player()).then(
                        argument("amount", IntegerArgumentType.integer())
                            .executes(context -> {
                                ServerPlayerEntity player =
                                    EntityArgumentType.getPlayer(
                                        context,
                                        "player"
                                    );
                                int amount = IntegerArgumentType.getInteger(
                                    context,
                                    "amount"
                                );
                                GiveMoney
                                        .execute(Kromer.config.KromerKey(), amount, Kromer.database.getWallet(player.getUuid()).address)
                                        .whenComplete((b, ex) -> {
                                            switch (b) {
                                                case Result.Ok<GiveMoney.GiveMoneyResponse> ok -> context.getSource().sendFeedback(() ->
                                                        Locale.use(
                                                                Locale.Messages.ADDED_KRO,
                                                                amount,
                                                                player.getEntityName()
                                                        ), false);
                                                case Result.Err<GiveMoney.GiveMoneyResponse> err -> context.getSource()
                                                        .sendFeedback(() ->
                                                                        Locale.use(Locale.Messages.ERROR, "Error: " + err.error() + " param: " + err.error().parameter())
                                                                , false);
                                            }
                                        })
                                        .join();

                                return Command.SINGLE_SUCCESS;
                            })
                    )
            );
        var executeWelfare = literal("welfare")
            .executes(context -> {
                Kromer.executeWelfare();
                return Command.SINGLE_SUCCESS;
            });

        var muteWelfare = literal("muteWelfare").executes(context -> {
                WelfareData welfareData = Solstice.playerData
                    .get(
                        Objects.requireNonNull(
                            context.getSource().getPlayer()
                        ).getUuid()
                    )
                    .getData(WelfareData.class);

                if (welfareData.welfareMuted) {
                    context
                        .getSource()
                        .sendFeedback(
                            () -> Locale.use(Locale.Messages.WELFARE_NOT_MUTED),
                            false
                        );
                } else {
                    context
                        .getSource()
                        .sendFeedback(
                            () -> Locale.use(Locale.Messages.WELFARE_MUTED),
                            false
                        );
                }

                welfareData.welfareMuted = !welfareData.welfareMuted;
                return Command.SINGLE_SUCCESS;
            });
        var optInOfWelfare = literal("optIn")
                .executes(context -> {
                    WelfareData welfareData = Solstice.playerData
                            .get(context.getSource().getPlayer().getUuid())
                            .getData(WelfareData.class);

                    if (welfareData.optedOut) {
                        welfareData.optedOut = false;
                        context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.OPTED_IN_WELFARE), false);
                    } else {
                        context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.ALREADY_OPTED_IN), false);
                    }
                    return 1;
                });

        var optOutOfWelfare = literal("optOut")
                .executes(context -> {
                    WelfareData welfareData = Solstice.playerData
                            .get(context.getSource().getPlayer().getUuid())
                            .getData(WelfareData.class);

                    if (welfareData.optedOut) {
                        context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.ALREADY_OPTED_OUT), false);
                        return 0;
                    }

                    context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.OPTOUT_WARNING), false);
                    context.getSource().sendFeedback(Text::empty, false);
                    context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.OPTOUT_INFO), false);
                    context.getSource().sendFeedback(Text::empty, false);
                    context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.CONFIRM_OPTOUT_BUTTON), false);

                    return 1;
                })
                .then(
                        literal("confirm").executes(context -> {
                            WelfareData welfareData = Solstice.playerData
                                    .get(context.getSource().getPlayer().getUuid())
                                    .getData(WelfareData.class);

                            welfareData.optedOut = true;

                            Wallet wallet = Kromer.database.getWallet(context.getSource().getPlayer().getUuid());
                            if (wallet == null) return 0;

                            GetAddress.execute(wallet.address).whenComplete((b, ex) -> {
                                if (b instanceof Result.Ok<GetAddress.GetAddressBody>(GetAddress.GetAddressBody address)) {
                                    MakeTransaction.execute(
                                            wallet.privatekey,
                                            "serverwelf",
                                            address.address.balance,
                                            "[rcc-kromer] relinquishing all kromer"
                                    ).whenComplete((z, ex2) -> {
                                        context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.OPTED_OUT_WELFARE), false);
                                    }).join();
                                }
                            }).join();

                            return 1;
                        })
                );

        var rootCommand = literal("kromer")
            .then(versionCommand)
            .then(infoCommand)
            .then(muteWelfare)
            .then(optInOfWelfare)
            .then(optOutOfWelfare)
            .then(giveWalletCommand
                .requires(scs -> scs.hasPermissionLevel(4)))
            .then(setMoneyCommand
                    .requires(scs -> scs.hasPermissionLevel(4)))
            .then(executeWelfare
                    .requires(scs -> scs.hasPermissionLevel(4)));

        dispatcher.register(rootCommand);
    }
}
