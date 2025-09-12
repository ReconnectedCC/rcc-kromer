package cc.reconnected.kromer.client;

import cc.reconnected.kromer.arguments.AddressArgumentType;
import cc.reconnected.kromer.arguments.KromerArgumentInfo;
import cc.reconnected.kromer.arguments.KromerArgumentType;
import cc.reconnected.kromer.client.networking.TransactionNotification;
import cc.reconnected.kromer.networking.S2CTransactionNotification;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.http.v1.GetPlayerByName;
import ovh.sad.jkromer.http.v1.GetPlayerByUuid;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class mainClient implements ClientModInitializer {
    public static final ExecutorService NETWORK_EXECUTOR = Executors.newCachedThreadPool();
    private long lastRequestTime = 0;
    private static final long MONEY_ANIM_DURATION_MS = 1200L;
    private static volatile long moneyAnimStartMs = 0L;
    private static volatile float moneyAnimAmount = 0f;

    @Override
    public void onInitializeClient() {
        ArgumentTypeRegistry.registerArgumentType(new ResourceLocation("rcc-kromer", "kromer_amount"), KromerArgumentType.class, new KromerArgumentInfo());
        ArgumentTypeRegistry.registerArgumentType(new ResourceLocation("rcc-kromer", "kromer_address"), AddressArgumentType.class, SingletonArgumentInfo.contextFree(AddressArgumentType::address));
        final double[] balance = {-1};
        //ClientPlayNetworking.registerGlobalReceiver(S2CTransactionNotification.IDENTIFIER, TransactionNotification::handle);
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

                    if (balance[0] == -1) {
                        guiGraphics.drawString(mc.font, "Loading..", x + mc.font.width("Balance: "), y, 0xAAAAAA, true);
                    } else {
                        guiGraphics.drawString(mc.font, balance[0] + "KRO", x + mc.font.width("Balance: "), y, 0x00AA00, true);
                    }
                });
            }
        });
    }
        /*
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("kromeranim")
                            .executes(ctx -> {
                                mainClient.triggerMoneyAnimation(1.0f);
                                ctx.getSource().sendFeedback(Component.literal("Triggered +1.00 KRO animation"));
                                return 1;
                            })
                            .then(ClientCommandManager.argument("amount", FloatArgumentType.floatArg(0.01f))
                                    .executes(ctx -> {
                                        float amt = FloatArgumentType.getFloat(ctx, "amount");
                                        mainClient.triggerMoneyAnimation(amt);
                                        ctx.getSource().sendFeedback(Component.literal(String.format(java.util.Locale.US, "Triggered +%.2f KRO animation", amt)));
                                        return 1;
                                    })
                            )
            );
        });
        // HUD overlay: show balance when the Tab player list is showing; also render the gain animation
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return;

            // Show balance at top-right while the player list key is held (Tab)
            if (mc.options.keyPlayerList.isDown()) {
                long now = System.currentTimeMillis();
                if (now - lastRequestTime >= 15000) {
                    lastRequestTime = now;
                    CompletableFuture
                            .supplyAsync(() -> GetPlayerByUuid.execute(mc.player.getStringUUID()), NETWORK_EXECUTOR)
                            .thenCompose(future -> future)
                            .whenComplete((b, ex) -> {
                                if (ex != null) return;
                                if (b instanceof Result.Ok) {
                                    GetPlayerByName.GetPlayerByResponse value = ((Result.Ok<GetPlayerByName.GetPlayerByResponse>) b).value();
                                    if (!value.data.isEmpty()) {
                                        balance[0] = value.data.get(0).balance;
                                    }
                                }
                            });
                }

                String left = "Balance: ";
                String right = (balance[0] == -1) ? "Loading.." : (balance[0] + "KRO");

                int colorLeft = 0x55FF55;
                int colorRight = (balance[0] == -1) ? 0xAAAAAA : 0x00AA00;

                int sw = mc.getWindow().getGuiScaledWidth();
                int margin = 6;

                int widthRight = mc.font.width(right);
                int widthLeft = mc.font.width(left);
                int xRight = sw - margin - widthRight;
                int xLeft = xRight - widthLeft;
                int y = 6;

                guiGraphics.drawString(mc.font, left, xLeft, y, colorLeft, true);
                guiGraphics.drawString(mc.font, right, xRight, y, colorRight, true);
            }

            // Render money gain fly-up animation (always)
            long start = moneyAnimStartMs;
            if (start > 0) {
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed < MONEY_ANIM_DURATION_MS) {
                    float t = elapsed / (float) MONEY_ANIM_DURATION_MS; // 0..1
                    int baseX = 10;
                    int baseY = 10;
                    int flyY = baseY - (int) (t * 14);
                    float scale = 1.0f + (1.0f - t) * 0.25f;
                    int color = 0x55FF55;
                    String text = String.format(Locale.US, "+%.2f KRO", moneyAnimAmount);

                    PoseStack pose = guiGraphics.pose();
                    pose.pushPose();
                    pose.translate(baseX, flyY, 0);
                    pose.scale(scale, scale, 1f);
                    guiGraphics.drawString(mc.font, text, 0, 0, color, true);
                    pose.popPose();
                } else {
                    moneyAnimStartMs = 0L;
                }
            }
        });
    }
    public static void triggerMoneyAnimation(float diff) {
        if (diff <= 0f) return; // only animate gains
        moneyAnimAmount = diff;
        moneyAnimStartMs = System.currentTimeMillis();
    }
    */
}