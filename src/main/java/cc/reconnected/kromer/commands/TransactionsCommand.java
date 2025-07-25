package cc.reconnected.kromer.commands;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import eu.pb4.placeholders.api.TextParserUtils;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import ovh.sad.jkromer.Errors;
import ovh.sad.jkromer.http.transactions.ListTransactions;
import ovh.sad.jkromer.http.transactions.ListTransactions.ListTransactionsBody;
import ovh.sad.jkromer.http.Result;

import java.util.Objects;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class TransactionsCommand {

    public static void register(
            CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess,
            CommandManager.RegistrationEnvironment environment
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
    public static int checkTransactions(CommandContext<ServerCommandSource> context)
            throws CommandSyntaxException {

        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;

        int page = 1;
        try {
            page = IntegerArgumentType.getInteger(context, "page");
        } catch (IllegalArgumentException ignored) {}

        if (!Kromer.kromerStatus) {
            source.sendFeedback(
                    () -> Locale.use(Locale.Messages.KROMER_UNAVAILABLE),
                    false
            );
            return 0;
        }

        int offset = (page - 1) * 10;
        int finalPage = page;
        ListTransactions.execute(10, offset)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        source.sendFeedback(
                                () -> Locale.use(Locale.Messages.ERROR, throwable),
                                false
                        );
                        return;
                    }

                    switch (result) {
                        case Result.Ok<ListTransactionsBody> ok -> {
                            var responseObj = ok.value();
                            source.sendFeedback(
                                    () -> Text.literal(
                                            "<green>Transactions for " +
                                                    player.getName().getString() +
                                                    " on page " +
                                                    finalPage +
                                                    ":<reset>"
                                    ),
                                    false
                            );

                            if (responseObj.transactions == null || responseObj.transactions.isEmpty()) {
                                source.sendFeedback(
                                        () -> Text.literal("<red>No transactions found<reset>"),
                                        false
                                );
                                return;
                            }

                            for (var transaction : responseObj.transactions) {
                                String color = Objects.equals(transaction.type, "transfer")
                                        ? "<aqua>"
                                        : "<gold>";
                                String transactionText = String.format(
                                        "%s%s: #%s, %s->%s: %.2f KRO, Metadata: '%s'<reset>",
                                        color,
                                        transaction.time,
                                        transaction.id,
                                        transaction.from,
                                        transaction.to,
                                        transaction.value,
                                        transaction.metadata
                                );

                                source.sendFeedback(() -> TextParserUtils.formatText(transactionText), false);
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
                            source.sendFeedback(() -> TextParserUtils.formatText(nav.toString()), false);
                        }

                        case Result.Err<ListTransactionsBody> err -> {
                            Errors.ErrorResponse e = err.error();
                            source.sendFeedback(
                                    () -> Locale.use(Locale.Messages.ERROR, e.error() + " (" + e.parameter() + ")"),
                                    false
                            );
                        }
                    }
                })
                .join();

        return 1;
    }
}