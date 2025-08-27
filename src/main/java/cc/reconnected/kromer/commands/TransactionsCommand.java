package cc.reconnected.kromer.commands;

import static cc.reconnected.kromer.Kromer.NETWORK_EXECUTOR;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.Locale.Messages;

import cc.reconnected.kromer.database.Wallet;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import eu.pb4.placeholders.api.TextParserUtils;
import ovh.sad.jkromer.http.addresses.GetAddressTransactions;
import ovh.sad.jkromer.http.Result;

import java.text.SimpleDateFormat;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;


public class TransactionsCommand {

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment
    ) {
        dispatcher.register(
                literal("transactions")
                        .executes(TransactionsCommand::checkTransactions)
                        .then(
                                argument("page", IntegerArgumentType.integer(1))
                                        .executes(TransactionsCommand::checkTransactions)
                        )
        );
    }
    public static int checkTransactions(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Wallet wallet = Kromer.database.getWallet(context.getSource().getPlayer().getUUID());

        if (!Kromer.kromerStatus) {
            context.getSource().sendSuccess(() -> Locale.use(Locale.Messages.KROMER_UNAVAILABLE), false);
            return 0;
        }

        if (wallet == null) {
            context.getSource().sendSuccess(() -> Locale.use(Locale.Messages.NO_WALLET), false);
            return 0;
        }

        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;

        int page = 1;
        try {
            page = IntegerArgumentType.getInteger(context, "page");
        } catch (IllegalArgumentException ignored) {}

        int offset = (page - 1) * 10;
        int finalPage = page;

        CompletableFuture
                .supplyAsync(() -> GetAddressTransactions.execute(wallet.address, false, 10, offset), NETWORK_EXECUTOR)
                .thenCompose(future -> future) // unwrap nested CompletableFuture<Result<...>>
                .whenComplete((result, throwable) -> {
                    source.getServer().execute(() -> {
                        if (throwable != null) {
                            source.sendSuccess(() -> Locale.use(Locale.Messages.ERROR, throwable), false);
                            return;
                        }

                        if (result instanceof Result.Ok<GetAddressTransactions.GetAddressTransactionsBody> ok) {
                            var responseObj = ok.value();

                            source.sendSuccess(() -> Locale.use(Locale.Messages.TRANSACTIONS_INFO, player.getScoreboardName(), finalPage), false);

                            if (responseObj.transactions == null || responseObj.transactions.isEmpty()) {
                                source.sendSuccess(() -> Locale.use(Locale.Messages.TRANSACTIONS_EMPTY), false);
                                return;
                            }

                            final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            f.setTimeZone(TimeZone.getTimeZone("UTC"));

                            for (var transaction : responseObj.transactions) {
                                source.sendSuccess(() -> Locale.useSafe(
                                        Messages.TRANSACTION,
                                        Objects.equals(transaction.type, "transfer") ? "<aqua>" : "<gold>",
                                        f.format(transaction.time), // Always UTC
                                        transaction.id,
                                        transaction.from,
                                        transaction.to,
                                        transaction.value,
                                        transaction.metadata
                                ), false);
                            }

                            boolean hasNextPage = responseObj.transactions.size() >= 10;
                            boolean hasPreviousPage = finalPage > 1;
                            StringBuilder nav = new StringBuilder("<green>Navigation: ");
                            if (hasPreviousPage) {
                                nav.append("<run_cmd:'/transactions ")
                                        .append(finalPage - 1)
                                        .append("'><aqua>[Prev. Page]</aqua></run_cmd> ");
                            }
                            if (hasNextPage) {
                                nav.append("<run_cmd:'/transactions ")
                                        .append(finalPage + 1)
                                        .append("'><aqua>[Next Page]</aqua></run_cmd>");
                            }
                            source.sendSuccess(() -> TextParserUtils.formatText(nav.toString()), false);

                        } else if (result instanceof Result.Err<GetAddressTransactions.GetAddressTransactionsBody> err) {
                            source.sendSuccess(() -> Locale.use(Locale.Messages.ERROR, err.error()), false);
                        }
                    });
                });

        return 1;
    }
}
