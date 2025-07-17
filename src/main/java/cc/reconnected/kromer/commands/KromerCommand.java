package cc.reconnected.kromer.commands;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.database.WelfareData;
import cc.reconnected.kromer.responses.MotdResponse;
import com.google.gson.Gson;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class KromerCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        var versionCommand = literal("version").executes(context -> {
            String modVersion = FabricLoader.getInstance()
                    .getModContainer("rcc-kromer")
                    .get()
                    .getMetadata()
                    .getVersion()
                    .getFriendlyString(); // WHY

            HttpRequest request;
            try {
                request = HttpRequest.newBuilder().uri(new URI(Kromer.config.KromerURL() + "api/krist/motd")).GET().build();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            Kromer.httpclient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
                MotdResponse motdResponse = new Gson().fromJson(response.body(), MotdResponse.class);

                context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.VERSION, modVersion, motdResponse.motdPackage.version), false);
            });
            return 1;
        });

        var infoCommand = literal("info").executes(context -> {
            Wallet wallet = Kromer.database.getWallet(context.getSource().getPlayer().getUuid());
            if(wallet == null) return 0;

            context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.KROMER_INFORMATION, wallet.address, wallet.address, wallet.privatekey, wallet.privatekey), false);
            if(!Kromer.kromerStatus) {
                context.getSource().sendFeedback(Text::empty, false);
                context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.KROMER_UNAVAILABLE), false);
                return 0;
            }
            return Command.SINGLE_SUCCESS;
        });

        var giveWalletCommand = literal("givewallet")
                .then(argument("player", EntityArgumentType.player()).requires(source -> source.hasPermissionLevel(4))
                        .executes(context -> {
                            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");

                            Kromer.firstLogin(player.getEntityName(), player.getUuid(), player);
                            return Command.SINGLE_SUCCESS;
                        })
                );


        var setMoneyCommand = literal("addMoney")
                .then(argument("player", EntityArgumentType.player())
                        .then(argument("amount", IntegerArgumentType.integer())
                                .requires(source -> source.hasPermissionLevel(4))
                                .executes(context -> {
                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                                    int amount = IntegerArgumentType.getInteger(context, "amount");

                                    Kromer.giveMoney(Kromer.database.getWallet(player.getUuid()), amount);
                                    context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.ADDED_KRO, amount, player.getEntityName()), false);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                );
        var executeWelfare = literal("welfare")
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    Kromer.executeWelfare();
                    return Command.SINGLE_SUCCESS;
                });

        var muteWelfare = literal("muteWelfare").executes(context -> {
            WelfareData welfareData = Solstice.playerData.get(Objects.requireNonNull(context.getSource().getPlayer()).getUuid()).getData(WelfareData.class);

            if(welfareData.welfareMuted) {
                context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.WELFARE_NOT_MUTED), false);
            } else {
                context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.WELFARE_MUTED), false);
            }

            welfareData.welfareMuted = !welfareData.welfareMuted;
            return Command.SINGLE_SUCCESS;
        });

        var optOutOfWelfare = literal("optOut")

                .executes(context -> {
            WelfareData welfareData = Solstice.playerData.get(context.getSource().getPlayer().getUuid()).getData(WelfareData.class);

            if(welfareData.optedOut) {
                context.getSource().sendFeedback(() -> Text.literal("You have already opted out of welfare.").formatted(Formatting.RED), false);
                return 0;
            }

            context.getSource().sendFeedback(() -> Text.literal("Opting out of welfare means your starting kromer will be removed, including all").formatted(Formatting.RED), false);
            context.getSource().sendFeedback(() -> Text.literal("starting kromer, and future welfare payments. Are you sure you want to opt out of welfare?").formatted(Formatting.RED), false);
            context.getSource().sendFeedback(Text::empty, false);
            context.getSource().sendFeedback(() -> Text.literal("INFO: You are able to opt in back to welfare, but you will not regain your starting kromer.").formatted(Formatting.YELLOW), false);
            context.getSource().sendFeedback(Text::empty, false);
            context.getSource().sendFeedback(() -> Text.literal("[CONFIRM]").styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kromer optOut confirm"))).formatted(Formatting.RED).formatted(Formatting.BOLD), false);

            return 1;
        })
                .then(literal("confirm")
                        .executes(context -> {
                            WelfareData welfareData = Solstice.playerData.get(context.getSource().getPlayer().getUuid()).getData(WelfareData.class);

                            welfareData.optedOut = true;

                            Wallet wallet = Kromer.database.getWallet(context.getSource().getPlayer().getUuid());
                            if(wallet == null) return 0;

                            //  todo get balance
                        // remove that amount from balance via kromer.givemoney(-balance) ok

                            return 1;
                        }));

        var rootCommand = literal("kromer").then(versionCommand).then(giveWalletCommand).then(setMoneyCommand).then(executeWelfare).then(infoCommand).then(muteWelfare);

        dispatcher.register(rootCommand);
    }
}
