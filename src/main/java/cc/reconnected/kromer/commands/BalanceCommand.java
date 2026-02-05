package cc.reconnected.kromer.commands;

import static cc.reconnected.kromer.Kromer.NETWORK_EXECUTOR;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import cc.reconnected.kromer.Kromer;
import cc.reconnected.kromer.Locale;
import cc.reconnected.kromer.arguments.AddressArgumentType;
import cc.reconnected.kromer.database.Wallet;
import cc.reconnected.kromer.networking.BalanceResponsePacket;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.addresses.GetAddress;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class        BalanceCommand {
    public static void register(
        CommandDispatcher<CommandSourceStack> dispatcher,
        CommandBuildContext registryAccess,
        Commands.CommandSelection environment
    ) {
        dispatcher.register(
            literal("balance").then(
                    argument("recipient", AddressArgumentType.address()).executes(e -> runBalance(e, true))
            ).executes(e -> runBalance(e, false))
        );
        dispatcher.register(
            literal("bal").then(
                    argument("recipient", AddressArgumentType.address()).executes(e -> runBalance(e, true))
            ).executes(e -> runBalance(e, false))
        );
    }

    private static int runBalance(CommandContext<CommandSourceStack> context, boolean hasRecipient) {
        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;

        String kristAddress;

        if(hasRecipient) {
            String recipientInput = AddressArgumentType.getAddress(context, "recipient");

            if (recipientInput.matches("^k[a-z0-9]{9}$") || recipientInput.matches("^(?:([a-z0-9-_]{1,32})@)?([a-z0-9]{1,64})\\.kro$")) {
                kristAddress = recipientInput;
            } else {
                GameProfile otherProfile = null;
                try {
                    otherProfile = Objects.requireNonNull(context
                                    .getSource()
                                    .getServer()
                                    .getProfileCache())
                            .get(recipientInput)
                            .orElse(null);
                } catch (Exception ignored) {
                }

                if (otherProfile == null) {
                    context
                            .getSource()
                            .sendSuccess(
                                    () ->
                                            Component.literal(
                                                    "User not found and not a valid address."
                                            ).withStyle(ChatFormatting.RED),
                                    false
                            );
                    return 0;
                }

                Wallet otherWallet = Kromer.database.getWallet(
                        otherProfile.getId()
                );
                if (otherWallet == null) {
                    context
                            .getSource()
                            .sendSuccess(
                                    () ->
                                            Component.literal(
                                                    "Other user does not have a wallet. They haven't joined recently."
                                            ).withStyle(ChatFormatting.RED),
                                    false
                            );
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
                .sendSuccess(
                    () -> Locale.use(Locale.Messages.KROMER_UNAVAILABLE),
                    false
                );
            return 0;
        }

        if (myWallet == null) {
            context
                .getSource()
                .sendSuccess(
                    () -> Locale.use(Locale.Messages.NO_WALLET),
                    false
                );
            return 0;
        }

        CompletableFuture
                .supplyAsync(() -> GetAddress.execute(kristAddress == null ? myWallet.address : kristAddress), NETWORK_EXECUTOR)
                .thenCompose(future -> future)
                .whenComplete((b, ex) -> {
                    if (ex != null) {
                        source.getServer().execute(() ->
                                source.sendSuccess(() -> Locale.use(Locale.Messages.ERROR, ex.getMessage()), false)
                        );
                        return;
                    }

                    if (b instanceof Result.Ok<GetAddress.GetAddressBody> ok) {
                        if(kristAddress == null) {
                            source.getServer().execute(() -> {
                                source.sendSuccess(
                                        () -> Locale.use(Locale.Messages.BALANCE, ok.value().address.balance),
                                        false
                                );

                                Kromer.balanceCache.put(myWallet.address, ok.value().address.balance);
                            });
                        } else {
                            source.getServer().execute(() -> {
                                source.sendSuccess(
                                        () -> Locale.use(Locale.Messages.BALANCE_OTHERS, kristAddress, ok.value().address.balance),
                                        false
                                );

                                Kromer.balanceCache.put(kristAddress, ok.value().address.balance);
                            });
                        }

                        ServerPlayNetworking.send(player, BalanceResponsePacket.ID, BalanceResponsePacket.serialise(ok.value().address.balance));
                    } else if (b instanceof Result.Err<GetAddress.GetAddressBody> err) {
                        source.getServer().execute(() ->
                                source.sendSuccess(
                                        () -> Locale.use(Locale.Messages.ERROR, err.error()),
                                        false
                                )
                        );
                    }
                });
        return 1;
    }
}
