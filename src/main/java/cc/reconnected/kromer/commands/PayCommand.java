package cc.reconnected.kromer.commands;

import static cc.reconnected.kromer.Kromer.NETWORK_EXECUTOR;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.arguments.AddressArgumentType;
import cc.reconnected.kromer.arguments.KromerArgumentType;
import cc.reconnected.kromer.database.Wallet;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.transactions.MakeTransaction;

public class PayCommand {

    private static final Map<UUID, PendingPayment> pendingPayments =
        new HashMap<>();

    private static class PendingPayment {
        String to;
        BigDecimal amount;
        String metadata;
        long createdAt; // in milliseconds
    }

    public static void register(
        CommandDispatcher<CommandSourceStack> dispatcher,
        CommandBuildContext registryAccess,
        Commands.CommandSelection environment
    ) {
        dispatcher.register(
            literal("confirm_pay").executes(PayCommand::confirmPay)
        );

        dispatcher.register(
            literal("pay").then(
                argument("recipient", AddressArgumentType.address()).then(
                    argument("amount", KromerArgumentType.kromerArg())
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

    private static int sendPayment(CommandContext<CommandSourceStack> context, PendingPayment payment) {
        ServerPlayer player;
        try {
            player = Objects.requireNonNull(context.getSource().getPlayer());
        } catch (NullPointerException e) {
            context.getSource().sendSuccess(
                    () -> Component.literal("You must be online to use this command.")
                            .withStyle(ChatFormatting.RED),
                    false
            );
            return 0;
        }

        Wallet wallet = Kromer.database.getWallet(player.getUUID());
        if (wallet == null) {
            context.getSource().sendSuccess(
                    () -> Locale.use(Locale.Messages.NO_WALLET),
                    false
            );
            return 0;
        }

        CompletableFuture
                .supplyAsync(() -> MakeTransaction.execute(wallet.privatekey, payment.to, payment.amount, payment.metadata), NETWORK_EXECUTOR)
                .thenCompose(future -> future)  // unwrap nested CompletableFuture<Result<...>>
                .whenComplete((result, ex) -> {
                    context.getSource().getServer().execute(() -> {
                        if (ex != null) {
                            context.getSource().sendSuccess(
                                    () -> Locale.use(Locale.Messages.ERROR, ex.getMessage()),
                                    false
                            );
                            return;
                        }
                        if (result instanceof Result.Ok<MakeTransaction.MakeTransactionResponse> ok) {
                            context.getSource().sendSuccess(
                                    () -> Locale.use(Locale.Messages.PAYMENT_CONFIRMED, payment.amount, payment.to),
                                    false
                            );
                        } else if (result instanceof Result.Err<MakeTransaction.MakeTransactionResponse> err) {
                            context.getSource().sendSuccess(
                                    () -> Locale.use(Locale.Messages.ERROR, err.error()),
                                    false
                            );
                        }
                    });
                });


        return 1;
    }


    private static int executePay(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try {
            player = Objects.requireNonNull(context.getSource().getPlayer());
        } catch (NullPointerException e) {
            context.getSource().sendSuccess(
                    () -> Component.literal("You must be online to use this command.")
                            .withStyle(ChatFormatting.RED),
                    false
            );
            return 0;
        }

        pendingPayments.remove(player.getUUID());

        String recipientInput = AddressArgumentType.getAddress(context, "recipient");

        String kristAddress = null;
        String recipientName = null;

        if (!Kromer.kromerStatus) {
            context
                .getSource()
                .sendSuccess(() -> Locale.use(Locale.Messages.KROMER_UNAVAILABLE),
                    false
                );
            return 0;
        }

        if (recipientInput.matches("^k[a-z0-9]{9}$") || recipientInput.matches("^(?:([a-z0-9-_]{1,32})@)?([a-z0-9]{1,64})\\.kro$")) {
            kristAddress = recipientInput;
            recipientName = recipientInput;
        } else {
            GameProfile otherProfile;
            try {
                otherProfile = context
                    .getSource()
                    .getServer()
                    .getProfileCache()
                    .get(recipientInput)
                    .orElse(null);
            } catch (Exception e) {
                otherProfile = null;
            }

            if (otherProfile == null) {
                context
                    .getSource()
                    .sendSuccess(
                        () ->
                            Component.literal(
                                "User not found and not a valid address."
                            ).withStyle(ChatFormatting.RED),
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
                    .sendSuccess(
                        () ->
                            Component.literal(
                                "Other user does not have a wallet. They haven't joined recently."
                            ).withStyle(ChatFormatting.RED),
                        false
                    );
                return 0;
            }

            kristAddress = otherWallet.address;
            recipientName = otherProfile.getName();
        }


        BigDecimal amount = KromerArgumentType.getBigDecimal(context, "amount");

        Wallet wallet = Kromer.database.getWallet(player.getUUID());

        if (wallet == null) {
            context
                .getSource()
                .sendSuccess(
                    () ->
                        Component.literal(
                            "You do not have a wallet. This should be impossible. Rejoin/contact a staff member."
                        ).withStyle(ChatFormatting.RED),
                    false
                );
            return 0;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("return", wallet.address);
        data.put("username", player.getScoreboardName());
        data.put("useruuid", player.getUUID());
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

        // if amount > 10 KRO, require confirmation
        if (payment.amount.compareTo(new BigDecimal(10)) > 0) {
            pendingPayments.put(player.getUUID(), payment);

            String finalRecipientName = recipientName;
            Component confirmButton = Component.literal("[Confirm]").withStyle(style ->
                style
                    .withColor(ChatFormatting.GREEN)
                    .withBold(true)
                    .withClickEvent(
                        new net.minecraft.network.chat.ClickEvent(
                            net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                            "/confirm_pay"
                        )
                    )
                    .withHoverEvent(
                        new net.minecraft.network.chat.HoverEvent(
                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                            Component.literal(
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
                .sendSuccess(
                    () ->
                        Component.literal("Are you sure you want to send ")
                            .withStyle(ChatFormatting.GREEN)
                            .append(
                                Component.literal(amount + "KRO ").withStyle(
                                    ChatFormatting.DARK_GREEN
                                )
                            )
                            .append(Component.literal("to ").withStyle(ChatFormatting.GREEN))
                            .append(
                                Component.literal(finalRecipientName).withStyle(
                                    ChatFormatting.DARK_GREEN
                                )
                            )
                            .append(Component.literal("? ").withStyle(ChatFormatting.GREEN))
                            .append(confirmButton),
                    false
                );
            return 1;
        } else {
            return sendPayment(context, payment);
        }
    }

    private static int confirmPay(CommandContext<CommandSourceStack> context) {
        PendingPayment payment;
        ServerPlayer player;
        try {
            player = Objects.requireNonNull(context.getSource().getPlayer());
            payment = pendingPayments.remove(player.getUUID());
        } catch (NullPointerException e) {
            context.getSource().sendSuccess(
                    () -> Component.literal("You must be online to use this command.")
                            .withStyle(ChatFormatting.RED),
                    false
            );
            return 0;
        }

        if (payment == null) {
            context.getSource().sendSuccess(
                    () -> Locale.use(Locale.Messages.NO_PENDING),
                    false
            );
            return 0;
        }

        return sendPayment(context, payment);
    }
}
