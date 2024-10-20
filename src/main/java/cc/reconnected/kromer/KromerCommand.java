package cc.reconnected.kromer;

import cc.reconnected.kromer.responses.WalletCreateResponse;
import cc.reconnected.kromer.responses.WalletResponse;
import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.MetaNode;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class KromerCommand {
        public static void register(CommandDispatcher< ServerCommandSource > dispatcher, CommandRegistryAccess
        registryAccess, CommandManager.RegistrationEnvironment environment) {
            dispatcher.register(
                    literal("kromer")
                            .requires(source -> source.hasPermissionLevel(4))
                            .executes(ctx -> {
                                Main.firstLogin(ctx.getSource().getName(), ctx.getSource().getPlayer().getUuid());
                                return 1;
                            })
                            .then(literal("givewallet").then(argument("player", EntityArgumentType.player()))
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
                                    })
                            )
            );

            dispatcher.register(literal("pay")
                    .then(argument("player", StringArgumentType.word())
                            .then(argument("amount", FloatArgumentType.floatArg(0f))
                                    .executes(context -> {
                                        //check if word is a player name
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
                    })
            )
                    )
            );
    }
}
