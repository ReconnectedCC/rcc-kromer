package cc.reconnected.kromer.commands;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.Locale.Messages;
import cc.reconnected.kromer.database.Wallet;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.alexdevs.solstice.api.text.Components;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.addresses.GetAddressTransactions;

import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;

import static cc.reconnected.kromer.Kromer.NETWORK_EXECUTOR;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;


public class TransactionsCommand {

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment
    ) {
        dispatcher.register(
                literal("transactions")
                        .executes(context -> checkTransactions(context, 1, false))
                        .then(argument("includeMined", BoolArgumentType.bool())
                                .executes(context -> checkTransactions(context, 1, BoolArgumentType.getBool(context, "includeMined"))))
                        .then(argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> checkTransactions(context, IntegerArgumentType.getInteger(context, "page"), false))
                                .then(argument("includeMined", BoolArgumentType.bool())
                                        .executes(context -> checkTransactions(context, IntegerArgumentType.getInteger(context, "page"), BoolArgumentType.getBool(context, "includeMined"))))
                        )
        );
    }

    public static int checkTransactions(CommandContext<CommandSourceStack> context, final int page, boolean includeMined) throws CommandSyntaxException {
        final var source = context.getSource();
        final var player = source.getPlayerOrException();

        Wallet wallet = Kromer.database.getWallet(player.getUUID());

        if (!Kromer.kromerStatus) {
            source.sendSuccess(() -> Locale.parse(Locale.Messages.KROMER_UNAVAILABLE), false);
            return 0;
        }

        if (wallet == null) {
            source.sendSuccess(() -> Locale.parse(Locale.Messages.NO_OWN_WALLET), false);
            return 0;
        }

        int limit = 10;
        int offset = (page - 1) * limit;

        CompletableFuture
                .supplyAsync(() -> GetAddressTransactions.execute(wallet.address, !includeMined, limit, offset), NETWORK_EXECUTOR)
                .thenCompose(future -> future) // unwrap nested CompletableFuture<Result<...>>
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        source.sendFailure(Locale.parse(Locale.Messages.ERROR, Map.of(
                                "error", Component.literal(throwable.getMessage())
                        )));
                        return;
                    }

                    if (result instanceof Result.Ok<GetAddressTransactions.GetAddressTransactionsBody> ok) {
                        var responseObj = ok.value();

                        if (responseObj.transactions == null || responseObj.transactions.isEmpty()) {
                            source.sendSuccess(() -> Locale.parse(Locale.Messages.TRANSACTIONS_EMPTY), false);
                            return;
                        }

                        final SimpleDateFormat dateFormat = new SimpleDateFormat("yy-MM-dd");
                        final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                        dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

                        var newLine = Component.literal("\n");
                        MutableComponent component = Component.empty();

                        component = component.append(Locale.parse(Locale.Messages.TRANSACTIONS_INFO, Map.of(
                                "player", player.getDisplayName(),
                                "page", Component.literal(String.valueOf(page))
                        )));

                        for (var transaction : responseObj.transactions) {
                            var template = switch(transaction.type) {
                                case "mined" -> Locale.Messages.TRANSACTION_MINED;
                                case "transfer" -> wallet.address.equals(transaction.from) ? Messages.TRANSACTION_OUT : Messages.TRANSACTION_IN;
                                default -> Messages.TRANSACTION_OTHER;
                            };

                            component = component
                                    .append(newLine)
                                    .append(Locale.parse(template, transaction.value, Map.of(
                                            "datetime", Component.literal(dateTimeFormat.format(transaction.time)),
                                            "date", Component.literal(dateFormat.format(transaction.time)),
                                            "type", Component.literal(transaction.type),
                                            "id", Component.literal(String.valueOf(transaction.id)),
                                            "sender", Component.nullToEmpty(transaction.from),
                                            "recipient", Component.literal(transaction.to)
                                    )));

                            if (transaction.metadata != null) {
                                component = component
                                        .append(Component.literal(" "))
                                        .append(Locale.parse(Messages.TRANSACTION_METADATA, Map.of(
                                                "metadata", Component.nullToEmpty(transaction.metadata)
                                        )));
                            }
                        }

                        boolean hasNextPage = responseObj.transactions.size() >= 10;
                        boolean hasPreviousPage = page > 1;


                        if (hasNextPage || hasPreviousPage) {
                            component = component.append(newLine);
                            if (hasPreviousPage) {
                                component = component.append(Components.button("Previous Page", "Click to go to previous page", "/transactions " + (page - 1) + " " + includeMined));
                            }

                            if (hasNextPage && hasPreviousPage) {
                                component = component.append(Component.literal(" "));
                            }

                            if (hasNextPage) {
                                component = component.append(Components.button("Next Page", "Click to go to next page", "/transactions " + (page + 1) + " " + includeMined));
                            }
                        }

                        final Component finalComponent = component;
                        source.sendSuccess(() -> finalComponent, false);

                    } else if (result instanceof Result.Err<GetAddressTransactions.GetAddressTransactionsBody> err) {
                        source.sendFailure(Locale.error(err.error().toString()));
                    }
                });

        return 1;
    }
}
