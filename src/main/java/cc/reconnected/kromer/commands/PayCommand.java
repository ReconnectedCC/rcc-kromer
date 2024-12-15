package cc.reconnected.kromer.commands;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import cc.reconnected.kromer.Main;
import cc.reconnected.kromer.Util;
import cc.reconnected.kromer.responses.ErrorResponse;
import cc.reconnected.kromer.responses.TransactionCreateResponse;
import cc.reconnected.kromer.responses.WalletCreateResponse;
import cc.reconnected.kromer.responses.WalletResponse;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.MetaNode;
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

public class PayCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        var rootCommand = literal("pay")
                .then(argument("player", StringArgumentType.word()))
                .then(argument("amount", FloatArgumentType.floatArg()))
                .executes(context -> {
                    return executePay(context);
                })
                // metadata is optional
                .then(argument("metadata", StringArgumentType.greedyString()))
                .executes(context -> {
                    return executePay(context);
                });

        dispatcher.register(rootCommand);
    }


    private static int executePay(CommandContext<ServerCommandSource> context) {
        String playerName = StringArgumentType.getString(context, "player");
        Float amount = FloatArgumentType.getFloat(context, "amount");
        String metadata = null;
        if (context.getNodes().size() > 3) { // 3 nodes: "pay", "player", "amount", and optionally "metadata" 
            metadata = StringArgumentType.getString(context, "metadata");
        }

        Math.round(amount);
        //Check if player is online
        if (context.getSource().getServer().getPlayerManager().getPlayer(playerName) == null || context.getSource().getServer().getPlayerManager().getPlayer(playerName).getUuid() == null) {
            context.getSource().sendFeedback(() -> Text.literal("Player is offline").formatted(Formatting.RED), false);
            return 0;
        }

        ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(playerName);
        ServerPlayerEntity thisPlayer = context.getSource().getPlayer();
        
        // check if this user has a wallet (luckperms shit set)
        // check if other user has a wallet (luckperms shit set)
        // attempt to send 
        User luckpermsUser = Main.userManager.getUser(thisPlayer.getUuid());
        User otherLuckpermsUser = Main.userManager.getUser(player.getUuid());

        if(Util.getWalletAddress(luckpermsUser) == null) {
            Main.firstLogin(thisPlayer.getName().getString(), thisPlayer.getUuid());
            luckpermsUser = Main.userManager.getUser(thisPlayer.getUuid());
        }

        if(Util.getWalletAddress(otherLuckpermsUser) == null) {
            Main.firstLogin(player.getName().getString(), player.getUuid());
            otherLuckpermsUser = Main.userManager.getUser(player.getUuid());
        }

        
        JsonObject obj = new JsonObject();
        obj.addProperty("password", Util.getWalletPassword(luckpermsUser));
        obj.addProperty("to", Util.getWalletAddress(otherLuckpermsUser));
        obj.addProperty("amount", amount);
        obj.addProperty("metadata", metadata);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder().uri(
                new URI(Main.config.KromerURL()+"api/v1/transaction/create")
            )
                .headers("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(obj.toString()))
                .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    
        Main.httpclient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
            if (throwable != null) {
                context.getSource().sendFeedback(() -> Text.literal("Encountered issue while attempting to create transaction: " + throwable).formatted(Formatting.RED), false);
                return;
            }

            String body = response.body();

            if (body == null) {
                context.getSource().sendFeedback(() -> Text.literal("Encountered issue while attempting to create transaction: No response body").formatted(Formatting.RED), false);
                return;
            }

            if (response.statusCode() != 200) {
                ErrorResponse errorResponse = new Gson().fromJson(body, ErrorResponse.class);
                context.getSource().sendFeedback(() -> Text.literal("Enountered issue while attempting to create transaction (" + errorResponse.message + "): " + errorResponse.description).formatted(Formatting.RED), false);
                return;
            }

            TransactionCreateResponse transactionResponse = new Gson().fromJson(body, TransactionCreateResponse.class);
            context.getSource().sendFeedback(() -> Text.literal("Sent " + amount + " kromer to " + playerName + "!").formatted(Formatting.GREEN), false);
        }).join();
        return 1;
    }
}
