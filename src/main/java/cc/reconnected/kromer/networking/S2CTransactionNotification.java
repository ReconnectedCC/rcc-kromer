package cc.reconnected.kromer.networking;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import ovh.sad.jkromer.models.Transaction;

public class S2CTransactionNotification {
    public static final ResourceLocation IDENTIFIER = new ResourceLocation("rcc-kromer", "transaction_notification");
    public FriendlyByteBuf serialize(float amount) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(amount);
        return buf;
    }
}
