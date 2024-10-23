package cc.reconnected.kromer.commands;

import cc.reconnected.kromer.Main;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class KromerCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        var versionCommand = literal("version").executes(context -> {
            // TODO: This.
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
                        context.getSource().sendFeedback(() -> Text.literal("Player is offline"), false);
                        return 0;
                    }
                    Main.firstLogin(playerName, context.getSource().getServer().getPlayerManager().getPlayer(playerName).getUuid());
                    return 1;
                });

        var rootCommand = literal("kromer").then(versionCommand).then(giveWalletCommand);

        dispatcher.register(rootCommand);
    }
}
