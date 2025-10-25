package cc.reconnected.kromer.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import ovh.sad.jkromer.models.Transaction;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;

public class TransactionPacket {
    public static final ResourceLocation ID = new ResourceLocation("rcc-kromer", "transaction");

    public static FriendlyByteBuf serialise(Transaction tx, BigDecimal balance) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        writeTransaction(buf, tx);
        buf.writeUtf(balance.toString());
        return buf;
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
}
