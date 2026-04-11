package cc.reconnected.kromer.commands;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.arguments.AddressArgumentType;
import cc.reconnected.kromer.arguments.KromerArgumentType;
import cc.reconnected.kromer.common.CommonMeta;
import cc.reconnected.kromer.database.Wallet;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.alexdevs.solstice.Solstice;
import me.alexdevs.solstice.api.text.Components;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.transactions.MakeTransaction;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static cc.reconnected.kromer.Kromer.NETWORK_EXECUTOR;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class PayCommand {
    private static final Map<UUID, PendingPayment> pendingPayments = new HashMap<>();

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment
    ) {
        dispatcher.register(
                literal("pay")
                        .then(argument("recipient", AddressArgumentType.address()).then(
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

        dispatcher.register(literal("pay_confirm").executes(PayCommand::confirmPay));
    }

    private static int sendPayment(CommandContext<CommandSourceStack> context, PendingPayment payment) throws CommandSyntaxException {
        final var source = context.getSource();
        ServerPlayer player = context.getSource().getPlayerOrException();

        Wallet wallet = Kromer.database.getWallet(player.getUUID());
        if (wallet == null) {
            context.getSource().sendFailure(Locale.parse(Locale.Messages.NO_OWN_WALLET));
            return 0;
        }

        CompletableFuture
                .supplyAsync(() -> MakeTransaction.execute(wallet.privatekey, payment.to, payment.amount, payment.metadata), NETWORK_EXECUTOR)
                .thenCompose(future -> future)
                .whenComplete((result, ex) -> context.getSource().getServer().execute(() -> {
                    if (ex != null) {
                        source.sendFailure(Locale.error(ex.getMessage()));
                        return;
                    }

                    if (result instanceof Result.Ok<MakeTransaction.MakeTransactionResponse> ok) {
                        source.sendSuccess(() -> Locale.parse(Locale.Messages.PAYMENT_CONFIRMED, payment.amount, Map.of(
                                "recipient", Component.literal(payment.to)
                        )), false);
                    } else if (result instanceof Result.Err<MakeTransaction.MakeTransactionResponse> err) {
                        source.sendFailure(Locale.error(err.error().toString()));
                    }
                }));


        return 1;
    }

    private static int executePay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final var source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        pendingPayments.remove(player.getUUID());

        String recipientInput = AddressArgumentType.getAddress(context, "recipient");

        String kristAddress = null;
        String recipientName = null;

        if (!Kromer.kromerStatus) {
            source.sendFailure(Locale.parse(Locale.Messages.KROMER_UNAVAILABLE));
            return 0;
        }

        if (recipientInput.matches("^k[a-z0-9]{9}$")
                || recipientInput.matches("^(?:([a-z0-9-_]{1,32})@)?([a-z0-9]{1,64})\\.kro$")) {
            kristAddress = recipientInput;
            recipientName = recipientInput;
        } else {
            GameProfile otherProfile = Solstice.getUserCache().getByName(recipientInput).orElse(null);
            if (otherProfile == null) {
                source.sendFailure(Locale.parse(Locale.Messages.USER_OR_ADDRESS_NOT_FOUND));
                return 0;
            }

            Wallet otherWallet = Kromer.database.getWallet(otherProfile.getId());

            if (otherWallet == null) {
                source.sendFailure(Locale.parse(Locale.Messages.OTHER_USER_NO_WALLET));
                return 0;
            }

            kristAddress = otherWallet.address;
            recipientName = otherProfile.getName();
        }

        BigDecimal amount = KromerArgumentType.getBigDecimal(context, "amount");

        Wallet wallet = Kromer.database.getWallet(player.getUUID());

        if (wallet == null) {
            source.sendFailure(Locale.parse(Locale.Messages.NO_OWN_WALLET));
            return 0;
        }

        CommonMeta commonMeta = new CommonMeta();

        if (recipientInput.matches("^(?:([a-z0-9-_]{1,32})@)?([a-z0-9]{1,64})\\.kro$")) {
            commonMeta.positionalEntries.add(recipientInput);
        }

        commonMeta.keywordEntries.put("return", wallet.address);
        commonMeta.keywordEntries.put("username", player.getScoreboardName());
        commonMeta.keywordEntries.put("useruuid", player.getUUID().toString());

        if (context.getNodes().size() > 3) {
            // 3 nodes: "pay", "player", "amount", and optionally "metadata"
            String metaString = StringArgumentType.getString(context, "metadata");
            commonMeta.addFromOther(CommonMeta.fromString(metaString));
        }

        PendingPayment payment = new PendingPayment();
        payment.to = kristAddress;
        payment.amount = amount;
        payment.metadata = commonMeta.toString();
        payment.createdAt = System.currentTimeMillis();

        // TODO: when over half of own balance?
        // if amount > 10 KRO, require confirmation
        if (payment.amount.compareTo(new BigDecimal(10)) > 0) {
            pendingPayments.put(player.getUUID(), payment);

            String finalRecipientName = recipientName;
            Component confirmButton = Components.button("Confirm", "Click to confirm payment", "/pay_confirm");

            source.sendSuccess(() -> Locale.parse(Locale.Messages.PAYMENT_CONFIRMATION, payment.amount, Map.of(
                    "recipient", Component.literal(finalRecipientName),
                    "confirmButton", confirmButton
            )), false);

            return 1;
        } else {
            return sendPayment(context, payment);
        }
    }

    private static int confirmPay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PendingPayment payment = pendingPayments.remove(player.getUUID());

        if (payment == null) {
            context.getSource().sendSuccess(() -> Locale.parse(Locale.Messages.NO_PENDING), false);
            return 0;
        }

        return sendPayment(context, payment);
    }

    private static class PendingPayment {
        String to;
        BigDecimal amount;
        String metadata;
        long createdAt; // in milliseconds
    }
}
