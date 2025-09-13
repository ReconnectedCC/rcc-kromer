package cc.reconnected.kromer.client;

import cc.reconnected.kromer.arguments.AddressArgumentType;
import cc.reconnected.kromer.arguments.KromerArgumentInfo;
import cc.reconnected.kromer.arguments.KromerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.resources.ResourceLocation;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.v1.GetPlayerByName;
import ovh.sad.jkromer.http.v1.GetPlayerByUuid;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class mainClient implements ClientModInitializer {
    public static final ExecutorService NETWORK_EXECUTOR = Executors.newCachedThreadPool();
    private long lastRequestTime = 0;

    @Override
    public void onInitializeClient() {
        ArgumentTypeRegistry.registerArgumentType(new ResourceLocation("rcc-kromer", "kromer_amount"), KromerArgumentType.class, new KromerArgumentInfo());
        ArgumentTypeRegistry.registerArgumentType(new ResourceLocation("rcc-kromer", "kromer_address"), AddressArgumentType.class, SingletonArgumentInfo.contextFree(AddressArgumentType::address));
        final double[] balance = {-1};

        ScreenEvents.AFTER_INIT.register((mc, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PauseScreen) {
                long now = System.currentTimeMillis();
                if (now - lastRequestTime >= 15000) {
                    lastRequestTime = now;

                    CompletableFuture
                            .supplyAsync(() -> GetPlayerByUuid.execute(mc.player.getStringUUID()), NETWORK_EXECUTOR)
                            .thenCompose(future -> future)
                            .orTimeout(1, TimeUnit.SECONDS)
                            .whenComplete((b, ex) -> {
                                if (ex != null) {
                                    balance[0] = -2;
                                    return;
                                };

                                if (b instanceof Result.Ok<GetPlayerByName.GetPlayerByResponse> value) {
                                    if (!value.value().data.isEmpty()) {
                                        balance[0] = value.value().data.get(0).balance;
                                    }
                                }
                            });
                }

                ScreenEvents.afterRender(screen).register((screen1, guiGraphics, mouseX, mouseY, tickDelta) -> {
                    int x = 10;
                    int y = 10;

                    guiGraphics.drawString(mc.font, "Balance: ", x, y, 0x55FF55, true);

                    x += mc.font.width("Balance: ");
                    if (balance[0] == -1) {
                        guiGraphics.drawString(mc.font, "Loading..", x, y, 0xAAAAAA, true);
                    } else if(balance[0] == -2) {
                        guiGraphics.drawString(mc.font, "Error..", x, y, 0xAA0000, true);
                    } else {
                        guiGraphics.drawString(mc.font, balance[0] + "KRO", x, y, 0x00AA00, true);
                    }

                    x = 10;
                });
            }
        });
    }
}