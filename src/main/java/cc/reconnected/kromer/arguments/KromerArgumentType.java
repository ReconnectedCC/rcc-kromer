package cc.reconnected.kromer.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collection;

public class KromerArgumentType implements ArgumentType<BigDecimal> {
    private static final Collection<String> EXAMPLES = Arrays.asList("1", "1.2", "1.23", ".5", ".12", "1234.56");
    private static final Collection<String> EXAMPLESWITHNEGATIVEVALS = Arrays.asList("1", "-1.2", "1.23", ".5", ".12", "-1234.56");

    private final boolean allowNegativeValues;
    private final boolean allowZero;

    private KromerArgumentType(boolean allowNegativeValues, boolean allowZero) {
        this.allowNegativeValues = allowNegativeValues;
        this.allowZero = allowZero;
    }

    public static KromerArgumentType kromerArg() {
        return kromerArg(false, false);
    }

    public static KromerArgumentType kromerArg(boolean allowNegativeValues, boolean allowZero) {
        return new KromerArgumentType(allowNegativeValues, allowZero);
    }

    public static BigDecimal getBigDecimal(final CommandContext<?> context, final String name) {
        return context.getArgument(name, BigDecimal.class);
    }

    public boolean getAllowNegativeValues() {
        return allowNegativeValues;
    }

    public boolean getAllowZero() {
        return allowZero;
    }

    @Override
    public BigDecimal parse(StringReader reader) throws CommandSyntaxException {
        float value = reader.readFloat();
        if (!allowNegativeValues && value < 0) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidFloat().createWithContext(reader, String.valueOf(value));
        }
        if (!allowZero && value == 0) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidFloat().createWithContext(reader, String.valueOf(value));
        }
        return new BigDecimal(value, new MathContext(2, RoundingMode.DOWN));
    }

    @Override
    public Collection<String> getExamples() {
        return (allowNegativeValues) ? EXAMPLESWITHNEGATIVEVALS : EXAMPLES;
    }
}

