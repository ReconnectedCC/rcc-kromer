package cc.reconnected.kromer.commands;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.database.Wallet;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.transactions.MakeTransaction;

public class PayCommand {

    private static final Map<UUID, PendingPayment> pendingPayments =
        new HashMap<>();

    private static class PendingPayment {
        String to;
        float amount;
        String metadata;
        long createdAt; // in milliseconds
    }

    public static void register(
        CommandDispatcher<ServerCommandSource> dispatcher,
        CommandRegistryAccess registryAccess,
        CommandManager.RegistrationEnvironment environment
    ) {
        dispatcher.register(
            literal("confirm_pay").executes(PayCommand::confirmPay)
        );

        dispatcher.register(
            literal("pay").then(
                argument("recipient", StringArgumentType.word()).then(
                    argument("amount", FloatArgumentType.floatArg())
                        .executes(PayCommand::executePay)
                        .then(
                            argument(
                                "metadata",
                                StringArgumentType.greedyString()
                            ).executes(PayCommand::executePay)
                        )
                )
            )
        );
    }

    public static String toSemicolonString(Map<String, Object> data) {
        return data
            .entrySet()
            .stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(";"));
    }

    private static int executePay(CommandContext<ServerCommandSource> context) {
        pendingPayments.remove(context.getSource().getPlayer().getUuid());

        String recipientInput = StringArgumentType.getString(
            context,
            "recipient"
        );
        String kristAddress = null;
        String recipientName = null;

        if (!Kromer.kromerStatus) {
            context
                .getSource()
                .sendFeedback(
                    () ->
                        Text.literal(
                            "Kromer is currently unavailable."
                        ).formatted(Formatting.RED),
                    false
                );
            return 0;
        }

        if (recipientInput.matches("^k[a-z0-9]{9}$")) {
            kristAddress = recipientInput;
            recipientName = recipientInput;
        } else if (recipientInput.matches("^(?:([a-z0-9-_]{1,32})@)?([a-z0-9]{1,64})\\.kro$")) {
            kristAddress = recipientInput;
            recipientName = recipientInput;
        } else {
            GameProfile otherProfile;
            try {
                otherProfile = context
                    .getSource()
                    .getServer()
                    .getUserCache()
                    .findByName(recipientInput)
                    .orElse(null);
            } catch (Exception e) {
                otherProfile = null;
            }

            if (otherProfile == null) {
                context
                    .getSource()
                    .sendFeedback(
                        () ->
                            Text.literal(
                                "User not found and not a valid address."
                            ).formatted(Formatting.RED),
                        false
                    );
                return 0;
            }

            Wallet otherWallet = Kromer.database.getWallet(
                otherProfile.getId()
            );
            if (otherWallet == null) {
                context
                    .getSource()
                    .sendFeedback(
                        () ->
                            Text.literal(
                                "Other user does not have a wallet. They haven't joined recently."
                            ).formatted(Formatting.RED),
                        false
                    );
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
            context
                .getSource()
                .sendFeedback(
                    () ->
                        Text.literal(
                            "You do not have a wallet. This should be impossible. Rejoin/contact a staff member."
                        ).formatted(Formatting.RED),
                    false
                );
            return 0;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("return", wallet.address);
        data.put("username", thisPlayer.getEntityName());
        data.put("useruuid", thisPlayer.getUuid());
        String metadata = toSemicolonString(data);

        if (context.getNodes().size() > 3) {
            // 3 nodes: "pay", "player", "amount", and optionally "metadata"
            metadata += ";" + StringArgumentType.getString(context, "metadata");
        }

        PendingPayment payment = new PendingPayment();
        payment.to = kristAddress;
        payment.amount = amount;
        payment.metadata = metadata;
        payment.createdAt = System.currentTimeMillis();

        pendingPayments.put(thisPlayer.getUuid(), payment);

        String finalRecipientName = recipientName;
        Text confirmButton = Text.literal("[Confirm]").styled(style ->
            style
                .withColor(Formatting.GREEN)
                .withBold(true)
                .withClickEvent(
                    new net.minecraft.text.ClickEvent(
                        net.minecraft.text.ClickEvent.Action.RUN_COMMAND,
                        "/confirm_pay"
                    )
                )
                .withHoverEvent(
                    new net.minecraft.text.HoverEvent(
                        net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
                        Text.literal(
                            "Click to confirm payment of " +
                            amount +
                            "KRO to " +
                            finalRecipientName
                        )
                    )
                )
        );

        context
            .getSource()
            .sendFeedback(
                () ->
                    Text.literal("Are you sure you want to send ")
                        .formatted(Formatting.GREEN)
                        .append(
                            Text.literal(amount + "KRO ").formatted(
                                Formatting.DARK_GREEN
                            )
                        )
                        .append(Text.literal("to ").formatted(Formatting.GREEN))
                        .append(
                            Text.literal(finalRecipientName).formatted(
                                Formatting.DARK_GREEN
                            )
                        )
                        .append(Text.literal("? ").formatted(Formatting.GREEN))
                        .append(confirmButton),
                false
            );
        return 1;
    }

    private static int confirmPay(CommandContext<ServerCommandSource> context) {
        PendingPayment payment;
        ServerPlayerEntity player;
        try {
            player = Objects.requireNonNull(context.getSource().getPlayer());
            payment = pendingPayments.remove(Objects.requireNonNull(context.getSource().getPlayer()).getUuid());
        } catch (NullPointerException e) {
            // Player is not online or command source is not a player
            context.getSource().sendFeedback(() -> Text.literal("You must be online to use this command.").formatted(Formatting.RED), false);
            return 0;
        }

        if (payment == null) {
            context
                .getSource()
                .sendFeedback(
                    () -> Locale.use(Locale.Messages.NO_PENDING),
                    false
                );
            return 0;
        }

        Wallet wallet = Kromer.database.getWallet(player.getUuid());

        if (wallet == null) {
            context
                .getSource()
                .sendFeedback(
                    () -> Locale.use(Locale.Messages.NO_WALLET),
                    false
                );
            return 0;
        }
        MakeTransaction.execute(wallet.privatekey, payment.to, payment.amount, payment.metadata)
                .whenComplete((b, ex) -> {
                    switch (b) {
                        case Result.Ok<MakeTransaction.MakeTransactionResponse> ok -> context
                                .getSource()
                                .sendFeedback(
                                        () ->
                                                Locale.use(
                                                        Locale.Messages.PAYMENT_CONFIRMED,
                                                        payment.amount,
                                                        payment.to
                                                ),
                                        false);
                        case Result.Err<MakeTransaction.MakeTransactionResponse> err -> context.getSource()
                                .sendFeedback(() ->
                                                Locale.use(Locale.Messages.ERROR, err.error())
                                        , false);
                    }
                }).join();
        return 1;
    }
}
