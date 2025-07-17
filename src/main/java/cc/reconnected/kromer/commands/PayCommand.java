package cc.reconnected.kromer.commands;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.responses.TransactionCreateResponse;
import cc.reconnected.kromer.responses.errors.GenericError;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
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
import java.util.UUID;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PayCommand {
    private static final Map<UUID, PendingPayment> pendingPayments = new HashMap<>();

    private static class PendingPayment {
        String to;
        float amount;
        String metadata;
        long createdAt; // in milliseconds
    }


    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("confirm_pay").executes(PayCommand::confirmPay));

        dispatcher.register(literal("pay")
                .then(argument("recipient", StringArgumentType.word())
                        .then(argument("amount", FloatArgumentType.floatArg())
                                .executes(PayCommand::executePay)
                                .then(argument("metadata", StringArgumentType.greedyString())
                                        .executes(PayCommand::executePay)))));
    }

    public static String toSemicolonString(Map<String, Object> data) {
        return data.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(";"));
    }


    private static int executePay(CommandContext<ServerCommandSource> context) {

        pendingPayments.remove(context.getSource().getPlayer().getUuid());

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

        float rawAmount = FloatArgumentType.getFloat(context, "amount");
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

        PendingPayment payment = new PendingPayment();
        payment.to = kristAddress;
        payment.amount = amount;
        payment.metadata = metadata;
        payment.createdAt = System.currentTimeMillis();

        pendingPayments.put(thisPlayer.getUuid(), payment);

        String finalRecipientName = recipientName;
        Text confirmButton = Text.literal("[Confirm]")
                .styled(style -> style
                        .withColor(Formatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new net.minecraft.text.ClickEvent(
                                net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/confirm_pay"))
                        .withHoverEvent(new net.minecraft.text.HoverEvent(
                                net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
                                Text.literal("Click to confirm payment of " + amount + "KRO to " + finalRecipientName))));

        context.getSource().sendFeedback(() ->
                Text.literal("Are you sure you want to send ").formatted(Formatting.GREEN)
                        .append(Text.literal(amount + "KRO ").formatted(Formatting.DARK_GREEN))
                        .append(Text.literal("to ").formatted(Formatting.GREEN))
                        .append(Text.literal(finalRecipientName).formatted(Formatting.DARK_GREEN))
                        .append(Text.literal("? ").formatted(Formatting.GREEN))
                        .append(confirmButton), false);
        return 1;
    }

    private static int confirmPay(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();

        PendingPayment payment = pendingPayments.remove(player.getUuid());

        if (payment == null) {
            context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.NO_PENDING), false);
            return 0;
        }

        Wallet wallet = Kromer.database.getWallet(player.getUuid());

        if (wallet == null) {
            context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.NO_WALLET), false);
            return 0;
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("privatekey", wallet.privatekey);
        obj.addProperty("to", payment.to);
        obj.addProperty("amount", payment.amount);
        obj.addProperty("metadata", payment.metadata);
        System.out.println("transaction " + obj.toString());
        System.out.println("url " + Kromer.config.KromerURL() + "api/krist/transactions");
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(new URI(Kromer.config.KromerURL() + "api/krist/transactions"))
                    .headers("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(obj.toString()))
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        Kromer.httpclient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
            if (throwable != null) {
                context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.ERROR, throwable), false);
                return;
            }

            if (response.statusCode() != 200) {
                GenericError error;
                try {
                    error = new Gson().fromJson(response.body(), GenericError.class);
                } catch (JsonSyntaxException jse) {
                    context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.ERROR, String.valueOf(response.statusCode())), false);
                    return;
                }
                context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.ERROR, error.error + " (" + error.parameter + ")"), false);
                return;
            }

            context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.PAYMENT_CONFIRMED, payment.amount, payment.to), false);
        }).join();

        return 1;
    }

}
