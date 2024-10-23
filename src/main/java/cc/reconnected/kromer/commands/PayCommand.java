package cc.reconnected.kromer.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PayCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        var rootCommand = literal("pay")
                .then(argument("player", StringArgumentType.word()))
                .then(argument("amount", FloatArgumentType.floatArg()))
                .executes(context -> {
                    String playerName = StringArgumentType.getString(context, "player");
                    Float amount = FloatArgumentType.getFloat(context, "amount");
                    Math.round(amount);
                    //Check if player is online
                    if (context.getSource().getServer().getPlayerManager().getPlayer(playerName) == null || context.getSource().getServer().getPlayerManager().getPlayer(playerName).getUuid() == null) {
                        context.getSource().sendFeedback(() -> Text.literal("Player is offline"), false);
                        return 0;
                    }
                    //Main.firstLogin(playerName, context.getSource().getServer().getPlayerManager().getPlayer(playerName).getUuid());
                    return 1;
                });

        dispatcher.register(rootCommand);
    }
}
