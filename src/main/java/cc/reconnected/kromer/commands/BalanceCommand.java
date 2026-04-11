package cc.reconnected.kromer.commands;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.arguments.AddressArgumentType;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.networking.BalanceResponsePacket;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import me.alexdevs.solstice.Solstice;
import me.alexdevs.solstice.api.command.LocalGameProfile;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.addresses.GetAddress;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cc.reconnected.kromer.Kromer.NETWORK_EXECUTOR;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class BalanceCommand {
    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment
    ) {
        dispatcher.register(
                literal("balance")
                        .then(argument("recipient", AddressArgumentType.address())
                                .suggests(LocalGameProfile::suggest)
                                .executes(e -> runBalance(e, true))
                        )
                        .executes(e -> runBalance(e, false))
        );

        dispatcher.register(
                literal("bal")
                        .then(argument("recipient", AddressArgumentType.address())
                                .suggests(LocalGameProfile::suggest)
                                .executes(e -> runBalance(e, true))
                        )
                        .executes(e -> runBalance(e, false))
        );
    }

    private static int runBalance(CommandContext<CommandSourceStack> context, boolean hasRecipient) {
        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;

        String kristAddress;

        if (hasRecipient) {
            String recipientInput = AddressArgumentType.getAddress(context, "recipient");

            if (recipientInput.matches("^k[a-z0-9]{9}$")
                    || recipientInput.matches("^(?:([a-z0-9-_]{1,32})@)?([a-z0-9]{1,64})\\.kro$")) {
                kristAddress = recipientInput;
            } else {

                GameProfile otherProfile = Solstice.getUserCache()
                        .getByName(recipientInput)
                        .orElse(null);

                if (otherProfile == null) {
                    context.getSource().sendFailure(Locale.parse(Locale.Messages.USER_OR_ADDRESS_NOT_FOUND));
                    return 0;
                }

                Wallet otherWallet = Kromer.database.getWallet(otherProfile.getId());

                if (otherWallet == null) {
                    context
                            .getSource()
                            .sendFailure(Locale.parse(Locale.Messages.OTHER_USER_NO_WALLET));
                    return 0;
                }

                kristAddress = otherWallet.address;
            }
        } else {
            kristAddress = null;
        }

        Wallet myWallet = Kromer.database.getWallet(player.getUUID());

        if (!Kromer.kromerStatus) {
            context
                    .getSource()
                    .sendFailure(Locale.parse(Locale.Messages.KROMER_UNAVAILABLE));
            return 0;
        }

        if (myWallet == null) {
            context
                    .getSource()
                    .sendFailure(Locale.parse(Locale.Messages.NO_OWN_WALLET));
            return 0;
        }

        CompletableFuture
                .supplyAsync(() -> GetAddress.execute(kristAddress == null ? myWallet.address : kristAddress), NETWORK_EXECUTOR)
                .thenCompose(future -> future)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        source.sendFailure(Locale.error(ex.getMessage()));
                        return;
                    }

                    if (result instanceof Result.Ok<GetAddress.GetAddressBody> ok) {
                        if (kristAddress == null) {
                            source.sendSuccess(() -> Locale.parse(Locale.Messages.BALANCE, ok.value().address.balance), false);
                            Kromer.balanceCache.put(myWallet.address, ok.value().address.balance);
                        } else {
                            source.sendSuccess(() -> Locale.parse(Locale.Messages.BALANCE_OTHERS, ok.value().address.balance,
                                    Map.of(
                                            "address", Component.literal(kristAddress)
                                    )), false);
                            Kromer.balanceCache.put(kristAddress, ok.value().address.balance);
                        }

                        if (!hasRecipient) {
                            ServerPlayNetworking.send(player, BalanceResponsePacket.ID, BalanceResponsePacket.serialise(ok.value().address.balance));
                        }
                    } else if (result instanceof Result.Err<GetAddress.GetAddressBody> err) {
                        source.sendFailure(Locale.error(err.error().toString()));
                    }
                });

        return 1;
    }
}
