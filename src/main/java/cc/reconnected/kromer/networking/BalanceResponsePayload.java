package cc.reconnected.kromer.networking;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.PrimitiveCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public record BalanceResponsePayload(BigDecimal balance) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("rcc-kromer", "balance_response");
    public static final CustomPacketPayload.Type<BalanceResponsePayload> TYPE = new CustomPacketPayload.Type<>(ID);


    public static final StreamCodec<RegistryFriendlyByteBuf, BalanceResponsePayload> CODEC = StreamCodec.of(
            (buf,balance) ->  buf.writeUtf(balance.toString()),
            (buf)  -> new BalanceResponsePayload(new BigDecimal(buf.readUtf()))
    );
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
