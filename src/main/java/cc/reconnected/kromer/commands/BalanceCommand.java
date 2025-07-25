package cc.reconnected.kromer.commands;

import static net.minecraft.server.command.CommandManager.literal;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.database.Wallet;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.addresses.GetAddress;
import ovh.sad.jkromer.jKromer;

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
        System.out.println("Asking for wallet: " + wallet.address);
        System.out.println(jKromer.endpoint + "/addresses/" + wallet.address);

        GetAddress.execute(wallet.address).whenComplete((b, ex) -> {
            switch (b) {
                case Result.Ok<GetAddress.GetAddressBody> ok -> context.getSource().sendFeedback(() -> Locale.use(Locale.Messages.BALANCE, ok.value().address.balance), false);
                case Result.Err<GetAddress.GetAddressBody> err -> context.getSource()
                        .sendFeedback(() ->
                                Locale.use(Locale.Messages.ERROR, "Error: " + String.format("%.10s", err.error()) + " param: " + err.error().parameter())
                        , false);
            }
        }).join();
        return 1;
    }
}
