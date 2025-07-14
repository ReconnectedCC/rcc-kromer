package cc.reconnected.kromer.commands;

import cc.reconnected.kromer.Main;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.responses.GetAddressResponse;
import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static cc.reconnected.kromer.Main.errorHandler;
import static net.minecraft.server.command.CommandManager.literal;

public class BalanceCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        var rootCommand = literal("balance").executes(context -> {
            var source = context.getSource();
            var player = source.getPlayer();
            assert player != null;

            Wallet wallet = Main.database.getWallet(player.getUuid());

            if(wallet == null) {
                return 0;
            }

            var url = String.format(Main.config.KromerURL() + "api/krist/addresses/%s", wallet.address);
            HttpRequest request;
            try {
                request = HttpRequest.newBuilder().uri(new URI(url)).GET().build();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            Main.httpclient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
                errorHandler(response, throwable);
                GetAddressResponse addressResponse = new Gson().fromJson(response.body(), GetAddressResponse.class);
                var feedback = String.format("Your balance is: %f", addressResponse.address.balance);
                source.sendFeedback(() -> Text.literal(feedback).formatted(Formatting.GREEN), false);
            }).join();

            return 1;
        });

        dispatcher.register(rootCommand);
    }
}
