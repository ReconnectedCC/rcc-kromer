package cc.reconnected.kromer.commands;

import static net.minecraft.server.command.CommandManager.literal;

import cc.reconnected.kromer.API;
import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.models.errors.GenericError;
import cc.reconnected.kromer.models.responses.GetAddressResponse;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class BalanceCommand {

    public static void register(
        CommandDispatcher<ServerCommandSource> dispatcher,
        CommandRegistryAccess registryAccess,
        CommandManager.RegistrationEnvironment environment
    ) {
        dispatcher.register(
            literal("balance").executes(BalanceCommand::runBalance)
        );
        dispatcher.register(
            literal("bal").executes(BalanceCommand::runBalance)
        );
    }

    private static int runBalance(CommandContext<ServerCommandSource> context) {
        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;

        Wallet wallet = Kromer.database.getWallet(player.getUuid());

        if (!Kromer.kromerStatus) {
            context
                .getSource()
                .sendFeedback(
                    () -> Locale.use(Locale.Messages.KROMER_UNAVAILABLE),
                    false
                );
            return 0;
        }

        if (wallet == null) {
            context
                .getSource()
                .sendFeedback(
                    () -> Locale.use(Locale.Messages.NO_WALLET),
                    false
                );
            return 0;
        }

        API.getBalance(wallet.address, context)
            .whenComplete((response, throwable) -> {
                if (throwable != null) return; // getbalance handles via context
                context
                    .getSource()
                    .sendFeedback(
                        () ->
                            Locale.use(
                                Locale.Messages.BALANCE,
                                response.address.balance
                            ),
                        false
                    );
            })
            .join();

        return 1;
    }
}
