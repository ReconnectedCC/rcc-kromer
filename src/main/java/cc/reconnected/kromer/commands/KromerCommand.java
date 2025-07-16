package cc.reconnected.kromer.commands;

import cc.reconnected.kromer.Kromer;

import cc.reconnected.kromer.responses.MotdResponse;
import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
                
                var feedback = String.format("Fabric version: %s, server version: %s", modVersion, motdResponse.motdPackage.version);
                context.getSource().sendFeedback(() -> Text.literal(feedback).formatted(Formatting.YELLOW), false);
            });
            return 1;
        });


        var giveWalletCommand = literal("givewallet")
                .then(argument("player", EntityArgumentType.player()).requires(source -> source.hasPermissionLevel(4))
                        .executes(context -> {
                            //check if word is a player name
                            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                            //Check if player is online
                            if (player == null || player.getUuid() == null) {
                                context.getSource().sendFeedback(() -> Text.literal("Player is offline").formatted(Formatting.RED), false);
                                return 0;
                            }
                            Kromer.firstLogin(player.getEntityName(), player.getUuid(), player);
                            return 1;
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
                                var feedback = String.format("Added money %d KOR to %s.", amount, player.getEntityName());
                                context.getSource().sendFeedback(() -> Text.literal(feedback).formatted(Formatting.YELLOW), false);
                                return 1;
                            })
                        )
                );
        var executeWelfare = literal("welfare")
                                .requires(source -> source.hasPermissionLevel(4))
                                .executes(context -> {
                                    Kromer.executeWelfare();
                                    return 1;
                                });
        var rootCommand = literal("kromer").then(versionCommand).then(giveWalletCommand).then(setMoneyCommand).then(executeWelfare);

        dispatcher.register(rootCommand);
    }
}
