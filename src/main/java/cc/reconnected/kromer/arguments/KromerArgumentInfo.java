package cc.reconnected.kromer.arguments;

import com.google.gson.JsonObject;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class KromerArgumentInfo implements ArgumentTypeInfo<KromerArgumentType, KromerArgumentInfo.Template> {
    public void serializeToNetwork(Template template, FriendlyByteBuf buffer) {
        buffer.writeBoolean(template.allowNegativeValues);
        buffer.writeBoolean(template.allowZero);
    }

    public @NotNull Template deserializeFromNetwork(FriendlyByteBuf buffer) {
        boolean allowNegative = buffer.readBoolean();
        boolean allowZero = buffer.readBoolean();
        return new Template(this, allowNegative, allowZero);
    }

    public void serializeToJson(Template template, JsonObject json) {
        json.addProperty("allowNegativeValues", template.allowNegativeValues);
        json.addProperty("allowZero", template.allowZero);
    }

    public @NotNull Template unpack(KromerArgumentType argument) {
        return new Template(this, argument.getAllowNegativeValues(), argument.getAllowZero());
    }

    public final class Template implements ArgumentTypeInfo.Template<KromerArgumentType> {
        final boolean allowNegativeValues;
        final boolean allowZero;

        Template(KromerArgumentInfo kromerArgumentInfo, boolean allowNegativeValues, boolean allowZero) {
            this.allowNegativeValues = allowNegativeValues;
            this.allowZero = allowZero;
        }

        public @NotNull KromerArgumentType instantiate(CommandBuildContext context) {
            return KromerArgumentType.kromerArg(
                this.allowNegativeValues,
                this.allowZero
            );
        }

        public @NotNull ArgumentTypeInfo<KromerArgumentType, ?> type() {
            return KromerArgumentInfo.this;
        }
    }
}
