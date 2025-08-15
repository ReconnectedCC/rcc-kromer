package cc.reconnected.kromer.commands;

import static cc.reconnected.kromer.Kromer.NETWORK_EXECUTOR;
import static net.minecraft.commands.Commands.literal;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.database.Wallet;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.addresses.GetAddress;

import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class BalanceCommand {

    public static void register(
        CommandDispatcher<CommandSourceStack> dispatcher,
        CommandBuildContext registryAccess,
        Commands.CommandSelection environment
    ) {
        dispatcher.register(
            literal("balance").executes(BalanceCommand::runBalance)
        );
        dispatcher.register(
            literal("bal").executes(BalanceCommand::runBalance)
        );
    }

    private static int runBalance(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;

        Wallet wallet = Kromer.database.getWallet(player.getUUID());

        if (!Kromer.kromerStatus) {
            context
                .getSource()
                .sendSuccess(
                    () -> Locale.use(Locale.Messages.KROMER_UNAVAILABLE),
                    false
                );
            return 0;
        }

        if (wallet == null) {
            context
                .getSource()
                .sendSuccess(
                    () -> Locale.use(Locale.Messages.NO_WALLET),
                    false
                );
            return 0;
        }

        // Run network call off the main thread
        CompletableFuture
                .supplyAsync(() -> GetAddress.execute(wallet.address), NETWORK_EXECUTOR)
                .thenCompose(future -> future) // because GetAddress.execute returns CompletableFuture<Result<...>>
                .whenComplete((b, ex) -> {
                    if (ex != null) {
                        source.getServer().execute(() ->
                                source.sendSuccess(() -> Locale.use(Locale.Messages.ERROR, ex.getMessage()), false)
                        );
                        return;
                    }
                    switch (b) {
                        case Result.Ok<GetAddress.GetAddressBody> ok ->
                                source.getServer().execute(() ->
                                        source.sendSuccess(() -> Locale.use(Locale.Messages.BALANCE, ok.value().address.balance), false)
                                );
                        case Result.Err<GetAddress.GetAddressBody> err ->
                                source.getServer().execute(() ->
                                        source.sendSuccess(() -> Locale.use(Locale.Messages.ERROR, err.error()), false)
                                );
                    }
                });
        return 1;
    }
}
