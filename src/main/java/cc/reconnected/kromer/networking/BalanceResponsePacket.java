package cc.reconnected.kromer.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class BalanceResponsePacket {
    public static final ResourceLocation ID = new ResourceLocation("rcc-kromer", "balance_response");

    public static FriendlyByteBuf serialise(float balance) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeFloat(balance);
        return buf;
    }
}
