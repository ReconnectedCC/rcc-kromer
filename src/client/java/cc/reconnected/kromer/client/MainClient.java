package cc.reconnected.kromer.client;

import cc.reconnected.kromer.arguments.AddressArgumentType;
import cc.reconnected.kromer.arguments.KromerArgumentInfo;
import cc.reconnected.kromer.arguments.KromerArgumentType;
import cc.reconnected.kromer.networking.BalanceRequestPayload;
import cc.reconnected.kromer.networking.BalanceResponsePayload;
import cc.reconnected.kromer.networking.TransactionPayload;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ovh.sad.jkromer.models.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class MainClient implements ClientModInitializer {
    private final AtomicReference<BigDecimal> balance = new AtomicReference<>(BigDecimal.valueOf(-1f));

    @Override
    public void onInitializeClient() {
        ArgumentTypeRegistry.registerArgumentType(
                ResourceLocation.fromNamespaceAndPath("rcc-kromer", "kromer_amount"),
                KromerArgumentType.class,
                new KromerArgumentInfo()
        );
        ArgumentTypeRegistry.registerArgumentType(
                ResourceLocation.fromNamespaceAndPath("rcc-kromer", "kromer_address"),
                AddressArgumentType.class,
                SingletonArgumentInfo.contextFree(AddressArgumentType::address)
        );

        AutoConfig.register(KromerClientConfig.class, GsonConfigSerializer::new);
        ConfigHolder<KromerClientConfig> config = AutoConfig.getConfigHolder(KromerClientConfig.class);
        PayloadTypeRegistry.playC2S().register(BalanceRequestPayload.TYPE, BalanceRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TransactionPayload.TYPE, TransactionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BalanceResponsePayload.TYPE, BalanceResponsePayload.CODEC);
        ClientPlayConnectionEvents.JOIN.register((packetListener, sender, client) -> ClientPlayNetworking.send(new BalanceRequestPayload()));


        ClientPlayNetworking.registerGlobalReceiver(TransactionPayload.TYPE, (payload, ctx) -> {
            Transaction tx = payload.tx();
            BigDecimal decimal = payload.balance();

            if (Objects.equals(decimal.toString(), "-1")) {
                balance.set(decimal);
            }
            if (ctx.client().getToasts().queued.size() < 3 && config.getConfig().toastPopup) {
                ctx.client().getToasts().addToast(
                        SystemToast.multiline(ctx.client(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                Component.literal("Transaction"),
                                Component.literal(String.format("Incoming %.02f KRO from %s! Balance is now %.02f KRO.", tx.value, tx.from, decimal))
                        )
                );
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(BalanceResponsePayload.TYPE, (payload, ctx) -> balance.set(payload.balance()));

        ScreenEvents.AFTER_INIT.register((mc, screen, sw, sh) -> {
            if (screen instanceof PauseScreen) {
                ScreenEvents.afterRender(screen).register((scr, guiGraphics, mouseX, mouseY, tickDelta) -> {
                    // if singleplayer, return
                    if (mc.isLocalServer()) {
                        return;
                    }

                    if (config.getConfig().balanceDisplay) {
                        int x = 10;
                        int y = 10;

                        guiGraphics.drawString(mc.font, "Balance: ", x, y, 0x55FF55, true);
                        x += mc.font.width("Balance: ");

                        BigDecimal bal = balance.get();
                        if (Objects.equals(bal.toString(), "-1.0")) {
                            guiGraphics.drawString(mc.font, "Loading...", x, y, 0xAAAAAA, true);
                        } else if (Objects.equals(bal.toString(), "-2.0")) {
                            guiGraphics.drawString(mc.font, "Error!", x, y, 0xAA0000, true);
                        } else {
                            // format bal to 2 decimal places
                            bal = bal.setScale(2, RoundingMode.DOWN);
                            guiGraphics.drawString(mc.font, bal + "KRO", x, y, 0x00AA00, true);
                        }
                    }
                });
            }
        });
    }
}
