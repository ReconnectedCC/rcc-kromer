package cc.reconnected.kromer.commands;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.responses.GetAddressResponse;
import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static cc.reconnected.kromer.Kromer.errorHandler;
import static net.minecraft.server.command.CommandManager.literal;

public class BalanceCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("balance").executes(BalanceCommand::runBalance));
        dispatcher.register(literal("bal").executes(BalanceCommand::runBalance));
    }

    private static int runBalance(CommandContext<ServerCommandSource> context) {
        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;

        Wallet wallet = Kromer.database.getWallet(player.getUuid());

        if(!Kromer.kromerStatus) {
            context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.KROMER_UNAVAILABLE), false);
            return 0;
        }

        if (wallet == null) {
            context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.NO_WALLET), false);
            return 0;
        }

        var url = String.format(Kromer.config.KromerURL() + "api/krist/addresses/%s", wallet.address);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder().uri(new URI(url)).GET().build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        Kromer.httpclient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
            if (errorHandler(response, throwable)) return;

            GetAddressResponse addressResponse = new Gson().fromJson(response.body(), GetAddressResponse.class);
            context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.BALANCE, addressResponse.address.balance), false);
        }).join();

        return 1;
    }
}
