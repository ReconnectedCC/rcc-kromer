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
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.shared.peripheral.monitor.MonitorBlockEntity;
import dan200.computercraft.shared.peripheral.monitor.ServerMonitor;
import me.alexdevs.solstice.Solstice;
import me.alexdevs.solstice.api.text.Components;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.transactions.MakeTransaction;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cc.reconnected.kromer.Kromer.NETWORK_EXECUTOR;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class PayCommand {
    private static final Map<UUID, PendingPayment> pendingPayments = new HashMap<>();

    private static final Pattern KROMER_ADDRESS = Pattern.compile("\\bk[a-z0-9]{9}\\b");
    private static final Pattern KROMER_KRO_ADDRESS = Pattern.compile("\\b(?:([a-z0-9-_]{1,32})@)?([a-z0-9]{1,64})\\.kro\\b");

    private static final int MONITOR_SCAN_RADIUS = 4;

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_RECIPIENTS = PayCommand::suggestRecipients;

    private static CompletableFuture<Suggestions> suggestRecipients(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemaining().toLowerCase();
        CommandSourceStack source = context.getSource();
        ServerPlayer self = source.getPlayer();

        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (self != null && player.getUUID().equals(self.getUUID())) {
                continue;
            }
            String name = player.getGameProfile().getName();
            if (name.toLowerCase().startsWith(remaining)) {
                builder.suggest(name, Component.literal("Pay " + name));
            }
        }

        if (self != null) {
            for (String addr : findAddressesOnNearbyMonitors(self)) {
                if (addr.toLowerCase().startsWith(remaining)) {
                    builder.suggest(addr, Component.literal("Pay address seen on monitor"));
                }
            }
        }

        return builder.buildFuture();
    }

    private static Set<String> findAddressesOnNearbyMonitors(ServerPlayer player) {
        Set<String> found = new LinkedHashSet<>();

        if (!(player.level() instanceof ServerLevel level)) {
            return found;
        }

        BlockPos center = player.blockPosition();
        BlockPos min = center.offset(-MONITOR_SCAN_RADIUS, -MONITOR_SCAN_RADIUS, -MONITOR_SCAN_RADIUS);
        BlockPos max = center.offset(MONITOR_SCAN_RADIUS, MONITOR_SCAN_RADIUS, MONITOR_SCAN_RADIUS);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos)) continue;

            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof MonitorBlockEntity monitorTile)) continue;

            ServerMonitor serverMonitor = monitorTile.getCachedServerMonitor();
            if (serverMonitor == null) continue;

            Terminal terminal = serverMonitor.getTerminal();
            if (terminal == null) continue;

            Matcher m1 = KROMER_ADDRESS.matcher("");
            Matcher m2 = KROMER_KRO_ADDRESS.matcher("");

            for (int y = 0; y < terminal.getHeight(); y++) {
                String line = terminal.getLine(y).toString();
                m1.reset(line);
                while (m1.find()) found.add(m1.group());
                m2.reset(line);
                while (m2.find()) found.add(m2.group());
            }
        }

        return found;
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment
    ) {
        dispatcher.register(
                literal("pay")
                        .then(argument("recipient", AddressArgumentType.address())
                                .suggests(SUGGEST_RECIPIENTS)
                                .then(
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
                        source.sendFailure(Locale.error(ex));
                        return;
                    }

                    if (result instanceof Result.Ok<MakeTransaction.MakeTransactionResponse> ok) {
                        source.sendSuccess(() -> Locale.parse(Locale.Messages.PAYMENT_CONFIRMED, payment.amount, Map.of(
                                "recipient", Component.literal(payment.to)
                        )), false);
                    } else if (result instanceof Result.Err<MakeTransaction.MakeTransactionResponse> err) {
                        source.sendFailure(Locale.error(err.error()));
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

        if (KROMER_ADDRESS.matcher(recipientInput).matches()
                || KROMER_KRO_ADDRESS.matcher(recipientInput).matches()) {
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
