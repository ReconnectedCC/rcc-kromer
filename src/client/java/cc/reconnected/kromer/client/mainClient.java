package cc.reconnected.kromer.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;

public class mainClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((mc, screen, scaledWidth, scaledHeight) -> {
            if(screen instanceof PauseScreen) {
                ScreenEvents.afterRender(screen).register((screen1, guiGraphics, mouseX, mouseY, tickDelta) -> {
                    int x = 10;
                    int y = 10;

                    guiGraphics.drawString(mc.font, "Balance: ", x, y, 0xffffff, true);
                    guiGraphics.drawString(mc.font,  " KRO" + String.valueOf(Math.random()*100), x+mc.font.width("Balance: "), y, 0xffffff, true);
                    //y += client.font.lineHeight + 2;
                });
            }
        });
    }

}