package cc.reconnected.kromer.commands;

import cc.reconnected.kromer.Main;
import cc.reconnected.kromer.responses.WalletResponse;
import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static net.minecraft.server.command.CommandManager.literal;

public class BalanceCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        var rootCommand = literal("balance").executes(context -> {
            var source = context.getSource();
            var player = source.getPlayer();
            assert player != null;

            var manager = Main.userManager;
            var user = manager.getUser(player.getUuid());
            assert user != null;

            String walletAddress = user.getCachedData().getMetaData().getMetaValue("wallet_address"); // My hated

            var url = String.format(Main.kromerURL + "api/v1/wallet/%s", walletAddress);
            HttpRequest request;
            try {
                request = HttpRequest.newBuilder().uri(new URI(url)).GET().build();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            Main.httpclient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
                Main.nuhuh(response, throwable);
                WalletResponse walletResponse = new Gson().fromJson(response.body(), WalletResponse.class);
                var feedback = String.format("Your balance is: %f", walletResponse.balance);
                source.sendFeedback(() -> Text.literal(feedback), false);
            }).join();

            return 1;
        });

        dispatcher.register(rootCommand);
    }
}
