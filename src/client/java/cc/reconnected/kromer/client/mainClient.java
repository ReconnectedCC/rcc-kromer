package cc.reconnected.kromer.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.PauseScreen;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.v1.GetPlayerByName;
import ovh.sad.jkromer.http.v1.GetPlayerByUuid;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class mainClient implements ClientModInitializer {
    public static final ExecutorService NETWORK_EXECUTOR = Executors.newCachedThreadPool();
    private long lastRequestTime = 0;

    @Override
    public void onInitializeClient() {
        final double[] balance = {-1};

        ScreenEvents.AFTER_INIT.register((mc, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PauseScreen) {
                long now = System.currentTimeMillis();
                if (now - lastRequestTime >= 15000) {
                    lastRequestTime = now;

                    CompletableFuture
                            .supplyAsync(() -> GetPlayerByUuid.execute(mc.player.getStringUUID()), NETWORK_EXECUTOR)
                            .thenCompose(future -> future)
                            .whenComplete((b, ex) -> {
                                if (ex != null) return;

                                if (b instanceof Result.Ok(GetPlayerByName.GetPlayerByResponse value)) {
                                    if (!value.data.isEmpty()) {
                                        balance[0] = value.data.get(0).balance;
                                    }
                                }
                            });
                }

                ScreenEvents.afterRender(screen).register((screen1, guiGraphics, mouseX, mouseY, tickDelta) -> {
                    int x = 10;
                    int y = 10;

                    guiGraphics.drawString(mc.font, "Balance: ", x, y, 0x55FF55, true);

                    if (balance[0] == -1) {
                        guiGraphics.drawString(mc.font, "Loading..", x + mc.font.width("Balance: "), y, 0xAAAAAA, true);
                    } else {
                        guiGraphics.drawString(mc.font, balance[0] + "KRO", x + mc.font.width("Balance: "), y, 0x00AA00, true);
                    }
                });
            }
        });
    }
}