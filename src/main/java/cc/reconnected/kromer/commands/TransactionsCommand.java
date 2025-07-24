package cc.reconnected.kromer.commands;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.models.errors.GenericError;
import cc.reconnected.kromer.models.responses.GetTransactionsFromAddressResponse;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import eu.pb4.placeholders.api.TextParserUtils;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
                    argument("page", IntegerArgumentType.integer(1)).executes(
                        TransactionsCommand::checkTransactions
                    )
                )
        );
    }
    public static int checkTransactions(CommandContext<ServerCommandSource> context) {
        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;

        int page = 1;
        try {
            page = IntegerArgumentType.getInteger(context, "page");
        } catch (IllegalArgumentException e) {
            // If the argument is not provided, default to page 1
        }
        Wallet wallet = Kromer.database.getWallet(player.getUuid());
        String address = wallet != null ? wallet.address : null;
        if (!Kromer.kromerStatus) {
            source.sendFeedback(
                () -> Locale.use(Locale.Messages.KROMER_UNAVAILABLE),
                false
            );
            return 0; // Return failure
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                .uri(
                    new java.net.URI(
                        Kromer.config.KromerURL() +
                        "api/krist/addresses/" +
                        address +
                        "/transactions/?limit=10&offset=" +
                        (page - 1) * 10
                    )
                )
                .GET()
                .build();
        } catch (java.net.URISyntaxException e) {
            throw new RuntimeException(e);
        }
        int finalPage = page;
        Kromer.httpclient
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete((response, throwable) -> {
                if (throwable != null) {
                    context
                        .getSource()
                        .sendFeedback(
                            () -> Locale.use(Locale.Messages.ERROR, throwable),
                            false
                        );
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
            GetTransactionsFromAddressResponse responseObj = new Gson().fromJson(response.body(), GetTransactionsFromAddressResponse.class);
            context.getSource().sendFeedback(() -> TextParserUtils.formatText("<green>Transactions for " + player.getName().getString() + " on page " + finalPage +":<reset>"), false);
            if (responseObj.transactions.isEmpty()) {
                context.getSource().sendFeedback(() -> TextParserUtils.formatText("<red>No transactions found<reset>"), false);
                return; // Return success with no transactions
            } else {
                for (var transaction : responseObj.transactions) {
                    String color = Objects.equals(transaction.type, "transfer") ? "<aqua>" : "<gold>";
                    String transactionText = String.format("%s%s: #%s, %s->%s: %.2f KRO, Metadata: '%s'<reset>",
                            color,
                            transaction.time,
                            transaction.id,
                            transaction.from,
                            transaction.to,
                            transaction.value,
                            transaction.metadata
                        );

                        context
                            .getSource()
                            .sendFeedback(
                                () ->
                                    TextParserUtils.formatText(transactionText),
                                false
                            );
                    }
                }
                boolean hasNextPage = responseObj.count >= 10;
                boolean hasPreviousPage = finalPage > 1;
                StringBuilder navigationText = new StringBuilder(
                    "<green>Navigation: "
                );
                if (hasPreviousPage) {
                    navigationText
                        .append("<run_cmd:'/transactions ")
                        .append(finalPage - 1)
                        .append("'><aqua>[Prev. Page]</aqua></run_cmd> ");
                }
                if (hasNextPage) {
                    navigationText
                        .append("<run_cmd:'/transactions ")
                        .append(finalPage + 1)
                        .append("'><aqua>[Next Page]</aqua></run_cmd>");
                }
                context
                    .getSource()
                    .sendFeedback(
                        () ->
                            TextParserUtils.formatText(
                                navigationText.toString()
                            ),
                        false
                    );
            })
            .join();

        return 1; // Return success
    }
}
