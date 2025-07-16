package cc.reconnected.kromer.commands;

import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.responses.TransactionCreateResponse;
import cc.reconnected.kromer.responses.errors.GenericError;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import cc.reconnected.kromer.Main;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
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
        var rootCommand = literal("pay") // /pay
                .then(argument("player", GameProfileArgumentType.gameProfile()) // pay <player>
                    .then(argument("amount", FloatArgumentType.floatArg()) // pay <player> <amount>
                            .executes(PayCommand::executePay) // pay <player> <amount>
                            .then(argument("metadata", StringArgumentType.greedyString())
                                    .executes(PayCommand::executePay)) // pay <player> <amount> [metadata]

                    )
                );

        dispatcher.register(rootCommand);
    }


    private static int executePay(CommandContext<ServerCommandSource> context) {
        GameProfile otherProfile;
        try {
            otherProfile = GameProfileArgumentType.getProfileArgument(context, "player").iterator().next();
        } catch (CommandSyntaxException e) {
            throw new RuntimeException(e);
        }

        if(otherProfile == null) {
            context.getSource().sendFeedback(() -> Text.literal("User does not exist.").formatted(Formatting.RED), false);
            return 0;
        }

        Float amount = FloatArgumentType.getFloat(context, "amount");
        String metadata = null;
        if (context.getNodes().size() > 3) { // 3 nodes: "pay", "player", "amount", and optionally "metadata" 
            metadata = StringArgumentType.getString(context, "metadata");
        }

        ServerPlayerEntity thisPlayer = context.getSource().getPlayer();

        Wallet wallet = Main.database.getWallet(thisPlayer.getUuid());
        Wallet otherWallet = Main.database.getWallet(otherProfile.getId());

        if(wallet == null) {
            context.getSource().sendFeedback(() -> Text.literal("You do not have a wallet. This should be impossible. Rejoin/contact a staff member.").formatted(Formatting.RED), false);
            return 0;
        }

        if(otherWallet == null) {
            context.getSource().sendFeedback(() -> Text.literal("Other user does not have a wallet. They haven't joined recently.").formatted(Formatting.RED), false);
            return 0;
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("password", wallet.password);
        obj.addProperty("to", otherWallet.address);
        obj.addProperty("amount", amount);
        obj.addProperty("metadata", metadata);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder().uri(
                new URI(Main.config.KromerURL()+"api/krist/transactions")
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
                GenericError errorResponse = new Gson().fromJson(body, GenericError.class);
                context.getSource().sendFeedback(() -> Text.literal("Enountered issue while attempting to create transaction (" + errorResponse.error + "): " + errorResponse.parameter).formatted(Formatting.RED), false);
                return;
            }

            TransactionCreateResponse transactionResponse = new Gson().fromJson(body, TransactionCreateResponse.class);
            context.getSource().sendFeedback(() -> Text.literal("Sent " + amount + " kromer to " + otherProfile.getName() + "!").formatted(Formatting.GREEN), false);
        }).join();
        return 1;
    }
}
