package cc.reconnected.kromer.commands;

import cc.reconnected.kromer.Main;
import cc.reconnected.kromer.responses.VersionResponse;
import cc.reconnected.kromer.responses.WalletResponse;

import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
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
            String modVersion = FabricLoader.getInstance().getAllMods().stream()
                .filter(jj -> jj.getMetadata().getId() == "rcc-kromer")
                .map(jj -> jj.getMetadata().getVersion().getFriendlyString())
                .findFirst()
                .orElse(null);

            HttpRequest request;
            try {
                request = HttpRequest.newBuilder().uri(new URI(Main.kromerURL + "api/v1/version")).GET().build();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            Main.httpclient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
                VersionResponse versionResponse = new Gson().fromJson(response.body(), VersionResponse.class);
                
                var feedback = String.format("Fabric version: %s, server version: %s", modVersion, versionResponse.version);
                context.getSource().sendFeedback(() -> Text.literal(feedback).formatted(Formatting.YELLOW), false);
            });
            return 1;
        });

        var giveWalletCommand = literal("givewallet")
                .then(argument("player", EntityArgumentType.player()))
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    //check if word is a player name
                    String playerName = EntityArgumentType.getPlayer(context, "player").getEntityName();
                    //Check if player is online
                    if (context.getSource().getServer().getPlayerManager().getPlayer(playerName) == null || context.getSource().getServer().getPlayerManager().getPlayer(playerName).getUuid() == null) {
                        context.getSource().sendFeedback(() -> Text.literal("Player is offline").formatted(Formatting.RED), false);
                        return 0;
                    }
                    Main.firstLogin(playerName, context.getSource().getServer().getPlayerManager().getPlayer(playerName).getUuid());
                    return 1;
                });

        var rootCommand = literal("kromer").then(versionCommand).then(giveWalletCommand);

        dispatcher.register(rootCommand);
    }
}
