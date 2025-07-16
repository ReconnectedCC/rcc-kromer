package cc.reconnected.kromer.commands;

import cc.reconnected.kromer.Kromer;
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
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PayCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        var rootCommand = literal("pay")
                .then(argument("recipient", StringArgumentType.word())
                        .then(argument("amount", FloatArgumentType.floatArg())
                                .executes(PayCommand::executePay)
                                .then(argument("metadata", StringArgumentType.greedyString())
                                        .executes(PayCommand::executePay))));

        dispatcher.register(rootCommand);
    }

    public static String toSemicolonString(Map<String, Object> data) {
        return data.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(";"));
    }


    private static int executePay(CommandContext<ServerCommandSource> context) {
        String recipientInput = StringArgumentType.getString(context, "recipient");
        String kristAddress = null;
        String recipientName = null;

        if(!Kromer.kromerStatus) {
            context.getSource().sendFeedback(() -> Text.literal("Kromer is currently unavailable.").formatted(Formatting.RED), false);
            return 0;
        }

        if (recipientInput.matches("^k[a-z0-9]{9}$")) {
            kristAddress = recipientInput;
            recipientName = recipientInput;
        } else {
            GameProfile otherProfile;
            try {
                otherProfile = context.getSource().getServer().getUserCache().findByName(recipientInput).orElse(null);
            } catch (Exception e) {
                otherProfile = null;
            }

            if (otherProfile == null) {
                context.getSource().sendFeedback(() -> Text.literal("User not found and not a valid address.").formatted(Formatting.RED), false);
                return 0;
            }

            Wallet otherWallet = Kromer.database.getWallet(otherProfile.getId());
            if (otherWallet == null) {
                context.getSource().sendFeedback(() -> Text.literal("Other user does not have a wallet. They haven't joined recently.").formatted(Formatting.RED), false);
                return 0;
            }

            kristAddress = otherWallet.address;
            recipientName = otherProfile.getName();
        }

        Float rawAmount = FloatArgumentType.getFloat(context, "amount");
        float amount = Math.round(rawAmount * 100f) / 100f;


        ServerPlayerEntity thisPlayer = context.getSource().getPlayer();

        Wallet wallet = Kromer.database.getWallet(thisPlayer.getUuid());

        if (wallet == null) {
            context.getSource().sendFeedback(() -> Text.literal("You do not have a wallet. This should be impossible. Rejoin/contact a staff member.").formatted(Formatting.RED), false);
            return 0;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("return", wallet.address);
        data.put("username", thisPlayer.getEntityName());
        data.put("useruuid", thisPlayer.getUuid());
        String metadata = toSemicolonString(data);

        if (context.getNodes().size() > 3) { // 3 nodes: "pay", "player", "amount", and optionally "metadata"
            metadata += ";" + StringArgumentType.getString(context, "metadata");
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("password", wallet.password);
        obj.addProperty("to", kristAddress);
        obj.addProperty("amount", amount);
        obj.addProperty("metadata", metadata);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder().uri(
                            new URI(Kromer.config.KromerURL() + "api/krist/transactions")
                    )
                    .headers("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(obj.toString()))
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        String finalRecipientName = recipientName;
        Kromer.httpclient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
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
            context.getSource().sendFeedback(() ->
                            Text.literal("Sent ").formatted(Formatting.GREEN)
                                    .append(Text.literal(amount + "KRO ").formatted(Formatting.DARK_GREEN))
                                    .append(Text.literal("to ").formatted(Formatting.GREEN))
                                    .append(Text.literal(finalRecipientName + "!").formatted(Formatting.DARK_GREEN))
                    , false);
        }).join();
        return 1;
    }
}
