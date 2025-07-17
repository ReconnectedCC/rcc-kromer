package cc.reconnected.kromer;

import static cc.reconnected.kromer.Kromer.*;

import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.models.domain.Transaction;
import cc.reconnected.kromer.models.errors.GenericError;
import cc.reconnected.kromer.models.responses.GetAddressResponse;
import cc.reconnected.kromer.models.responses.WalletCreateResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.command.ServerCommandSource;

public class API {

    public static void giveMoney(Wallet wallet, float amount) {
        JsonObject giveMoneyObject = new JsonObject();
        giveMoneyObject.addProperty("address", wallet.address);
        giveMoneyObject.addProperty("amount", amount);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                .uri(
                    new URI(
                        config.KromerURL() + "api/_internal/wallet/give-money"
                    )
                )
                .headers(
                    "Kromer-Key",
                    config.KromerKey(),
                    "Content-Type",
                    "application/json"
                )
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        giveMoneyObject.toString()
                    )
                )
                .build();
        } catch (URISyntaxException e) {
            return; // thug it out
        }

        httpclient
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete((response, throwable) -> {
                if (errorHandler(response, throwable)) return;
            })
            .join();
    }

    public static CompletableFuture<HttpResponse<String>> startWs()
        throws URISyntaxException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(new URI(Kromer.config.KromerURL() + "api/krist/ws/start"))
            .headers("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    (new JsonObject()).toString()
                )
            )
            .build();

        return Kromer.httpclient.sendAsync(
            request,
            HttpResponse.BodyHandlers.ofString()
        );
    }

    public static boolean apiErrorHandler(
        CommandContext<ServerCommandSource> context,
        Throwable throwable,
        HttpResponse<String> response
    ) {
        if (throwable != null) {
            context
                .getSource()
                .sendFeedback(
                    () -> Locale.use(Locale.Messages.ERROR, throwable),
                    false
                );
            return true;
        }

        if (response.statusCode() != 200) {
            GenericError error;
            try {
                error = new Gson().fromJson(
                    response.body(),
                    GenericError.class
                );
            } catch (JsonSyntaxException jse) {
                context
                    .getSource()
                    .sendFeedback(
                        () ->
                            Locale.use(
                                Locale.Messages.ERROR,
                                String.valueOf(response.statusCode())
                            ),
                        false
                    );
                return true;
            }
            context
                .getSource()
                .sendFeedback(
                    () ->
                        Locale.use(
                            Locale.Messages.ERROR,
                            error.error + " (" + error.parameter + ")"
                        ),
                    false
                );
            return true;
        }

        return false;
    }

    public static CompletableFuture<GetAddressResponse> getBalance(
        String address,
        CommandContext<ServerCommandSource> context
    ) {
        var url = String.format(
            Kromer.config.KromerURL() + "api/krist/addresses/%s",
            address
        );
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder().uri(new URI(url)).GET().build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        CompletableFuture<GetAddressResponse> completableFuture =
            new CompletableFuture<>();

        Kromer.httpclient
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete((response, throwable) -> {
                if (context != null) {
                    if (apiErrorHandler(context, throwable, response)) {
                        completableFuture.completeExceptionally(
                            new Error("api error, handled via context")
                        );
                        return;
                    }
                } else {
                    // TODO: implement ts one day
                }

                GetAddressResponse addressResponse = new Gson().fromJson(
                    response.body(),
                    GetAddressResponse.class
                );
                completableFuture.complete(addressResponse);
            })
            .join();
        return completableFuture;
    }

    public static CompletableFuture<Wallet> createWallet(
        String name,
        UUID uuid
    ) {
        JsonObject playerObject = new JsonObject();
        playerObject.addProperty("name", name);
        playerObject.addProperty("uuid", uuid.toString());
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                .uri(
                    new URI(config.KromerURL() + "api/_internal/wallet/create")
                )
                .headers(
                    "Kromer-Key",
                    config.KromerKey(),
                    "Content-Type",
                    "application/json"
                )
                .POST(
                    HttpRequest.BodyPublishers.ofString(playerObject.toString())
                )
                .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        CompletableFuture<Wallet> completableFuture = new CompletableFuture<>();

        httpclient
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete((response, throwable) -> {
                if (errorHandler(response, throwable)) return;
                WalletCreateResponse walletResponse = new Gson().fromJson(
                    response.body(),
                    WalletCreateResponse.class
                );
                Transaction[] array = {};
                Wallet wallet = new Wallet(
                    walletResponse.address,
                    walletResponse.privatekey,
                    array,
                    array
                );
                database.setWallet(uuid, wallet);

                completableFuture.complete(wallet);
            })
            .join();

        return completableFuture;
    }
}
