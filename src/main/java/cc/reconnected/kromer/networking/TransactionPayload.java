package cc.reconnected.kromer.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ovh.sad.jkromer.models.Transaction;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;

public record TransactionPayload(Transaction tx, BigDecimal balance) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("rcc-kromer", "transaction");
    public static final CustomPacketPayload.Type<TransactionPayload> TYPE = new CustomPacketPayload.Type<>(ID);


    public static final StreamCodec<FriendlyByteBuf, TransactionPayload> CODEC = StreamCodec.of(
            TransactionPayload::write, TransactionPayload::read
    );

    private static void write(FriendlyByteBuf buf, TransactionPayload packet) {
        writeTransaction(buf, packet.tx());
        buf.writeUtf(packet.balance().toString());
    }

    private static TransactionPayload read(FriendlyByteBuf buf) {
        Transaction tx = readTransaction(buf);
        BigDecimal balance = new BigDecimal(buf.readUtf());
        return new TransactionPayload(tx, balance);
    }

    public static void writeTransaction(FriendlyByteBuf buf, Transaction tx) {
        buf.writeUtf(Objects.requireNonNullElse(tx.sent_metaname, ""));
        buf.writeInt(tx.id);
        buf.writeUtf(tx.from);
        buf.writeUtf(tx.to);
        buf.writeUtf(tx.value.toString());
        buf.writeLong(tx.time.getTime());
        buf.writeUtf(Objects.requireNonNullElse(tx.name, ""));
        buf.writeUtf(Objects.requireNonNullElse(tx.metadata, ""));
        buf.writeUtf(Objects.requireNonNullElse(tx.sent_name, ""));
        buf.writeUtf(tx.type);
    }

    public static Transaction readTransaction(FriendlyByteBuf buf) {
        String sent_metaname = buf.readUtf();
        int id = buf.readInt();
        String from = buf.readUtf();
        String to = buf.readUtf();
        BigDecimal value = new BigDecimal(buf.readUtf());
        Date time = new Date(buf.readLong());
        String name = buf.readUtf();
        String metadata = buf.readUtf();
        String sent_name = buf.readUtf();
        String type = buf.readUtf();

        return new Transaction(sent_metaname, id, from, to, value, time, name, metadata, sent_name, type);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
