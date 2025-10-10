package cc.reconnected.kromer.client;

import cc.reconnected.kromer.arguments.AddressArgumentType;
import cc.reconnected.kromer.arguments.KromerArgumentInfo;
import cc.reconnected.kromer.arguments.KromerArgumentType;
import cc.reconnected.kromer.networking.BalanceRequestPacket;
import cc.reconnected.kromer.networking.BalanceResponsePacket;
import cc.reconnected.kromer.networking.TransactionPacket;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ovh.sad.jkromer.models.Transaction;

import java.util.concurrent.atomic.AtomicReference;

public class mainClient implements ClientModInitializer {
    private final AtomicReference<Float> balance = new AtomicReference<>(-1f);

    @Override
    public void onInitializeClient() {
        ArgumentTypeRegistry.registerArgumentType(
                new ResourceLocation("rcc-kromer", "kromer_amount"),
                KromerArgumentType.class,
                new KromerArgumentInfo()
        );
        ArgumentTypeRegistry.registerArgumentType(
                new ResourceLocation("rcc-kromer", "kromer_address"),
                AddressArgumentType.class,
                SingletonArgumentInfo.contextFree(AddressArgumentType::address)
        );

        ClientPlayConnectionEvents.JOIN.register((packetListener, sender, client) -> {
                ClientPlayNetworking.send(BalanceRequestPacket.ID, new FriendlyByteBuf(Unpooled.buffer()));
        });


        ClientPlayNetworking.registerGlobalReceiver(TransactionPacket.ID, (client, handler, buf, responseSender) -> {
            Transaction tx = TransactionPacket.readTransaction(buf);
            float bal = buf.readFloat();

            if (bal != -1) balance.set(bal);
            if(client.getToasts().queued.size() < 3) {
                client.getToasts().addToast(SystemToast.multiline(client, SystemToast.SystemToastIds.TUTORIAL_HINT,
                        Component.literal("Transaction"),
                        Component.literal("Incoming " + tx.value + "KRO from " + tx.from + "! Balance is now " + String.format("%.2fKRO", Math.floor(balance.get() * 100) / 100.0))));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(BalanceResponsePacket.ID, (client, handler, buf, responseSender) -> {
            balance.set(buf.readFloat());
        });

        ScreenEvents.AFTER_INIT.register((mc, screen, sw, sh) -> {
            if (screen instanceof PauseScreen) {
                ScreenEvents.afterRender(screen).register((scr, guiGraphics, mouseX, mouseY, tickDelta) -> {
                    int x = 10;
                    int y = 10;

                    guiGraphics.drawString(mc.font, "Balance: ", x, y, 0x55FF55, true);
                    x += mc.font.width("Balance: ");

                    Float bal = balance.get();
                    if (bal == -1) {
                        guiGraphics.drawString(mc.font, "Loading..", x, y, 0xAAAAAA, true);
                    } else if (bal == -2) {
                        guiGraphics.drawString(mc.font, "Error..", x, y, 0xAA0000, true);
                    } else {
                        String balStr = String.format("%.2fKRO", Math.floor(bal * 100) / 100.0);
                        guiGraphics.drawString(mc.font, balStr, x, y, 0x00AA00, true);
                    }
                });
            }
        });
    }
}
