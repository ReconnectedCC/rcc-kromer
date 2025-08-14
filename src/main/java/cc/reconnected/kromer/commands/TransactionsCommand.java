package cc.reconnected.kromer.commands;

import static cc.reconnected.kromer.Kromer.NETWORK_EXECUTOR;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.Locale.Messages;

import cc.reconnected.kromer.database.Wallet;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import eu.pb4.placeholders.api.TextParserUtils;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import ovh.sad.jkromer.http.addresses.GetAddressTransactions;
import ovh.sad.jkromer.http.Result;

import java.text.SimpleDateFormat;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;


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
    public static int checkTransactions(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Wallet wallet = Kromer.database.getWallet(context.getSource().getPlayer().getUuid());

        if (!Kromer.kromerStatus) {
            context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.KROMER_UNAVAILABLE), false);
            return 0;
        }

        if (wallet == null) {
            context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.NO_WALLET), false);
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

        // Run the network call asynchronously on NETWORK_EXECUTOR
        CompletableFuture
                .supplyAsync(() -> GetAddressTransactions.execute(wallet.address, false, 10, offset), NETWORK_EXECUTOR)
                .thenCompose(future -> future) // unwrap nested CompletableFuture<Result<...>>
                .whenComplete((result, throwable) -> {
                    source.getServer().execute(() -> {
                        if (throwable != null) {
                            source.sendFeedback(() -> Locale.use(Locale.Messages.ERROR, throwable), false);
                            return;
                        }

                        switch (result) {
                            case Result.Ok<GetAddressTransactions.GetAddressTransactionsBody> ok -> {
                                var responseObj = ok.value();

                                source.sendFeedback(() -> Locale.use(Locale.Messages.TRANSACTIONS_INFO, player.getEntityName(), finalPage), false);

                                if (responseObj.transactions == null || responseObj.transactions.isEmpty()) {
                                    source.sendFeedback(() -> Locale.use(Locale.Messages.TRANSACTIONS_EMPTY), false);
                                    return;
                                }

                                final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                f.setTimeZone(TimeZone.getTimeZone("UTC"));

                                for (var transaction : responseObj.transactions) {source.sendFeedback(() -> Locale.useSafe(Messages.TRANSACTION,
                                            Objects.equals(transaction.type, "transfer") ? "<aqua>" : "<gold>",
                                            f.format(transaction.time), // Always UTC
                                            transaction.id,
                                            transaction.from,
                                            transaction.to,
                                            transaction.value,
                                            transaction.metadata), false);
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
                            case Result.Err<GetAddressTransactions.GetAddressTransactionsBody> err -> {
                                source.sendFeedback(() -> Locale.use(Locale.Messages.ERROR, err.error()), false);
                            }
                        }
                    });
                });

        return 1;
    }
}
