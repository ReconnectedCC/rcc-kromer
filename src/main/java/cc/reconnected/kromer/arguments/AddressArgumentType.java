package cc.reconnected.kromer.arguments;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import java.util.Arrays;
import java.util.Collection;

public class AddressArgumentType implements ArgumentType<String> {
    private static final Collection<String> EXAMPLES = Arrays.asList("kr0merwelf", "reconnected.kro", "meta@reconnected.kro", "hartbreix", "g6ys", "Dimaguy", "EmmaKnijn");
    public static boolean isAllowed(final char c) {
        return c >= '0' && c <= '9'
                || c >= 'A' && c <= 'Z'
                || c >= 'a' && c <= 'z'
                || c == '@' || c == '.'
                || c == '_';
    }
    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        final int start = reader.getCursor();
        int amountOfDots = 0;
        int amountofAts = 0;
        while (reader.canRead() && isAllowed(reader.peek())) {
            if (reader.peek() == '.') {
                amountOfDots++;
                if (amountOfDots > 1) {
                    throw new SimpleCommandExceptionType(new LiteralMessage("Unexpected .")).createWithContext(reader);
                }
            } else if (reader.peek() == '@') {
                amountofAts++;
                if (amountofAts > 1) {
                    throw new SimpleCommandExceptionType(new LiteralMessage("Unexpected @")).createWithContext(reader);
                }
            }
            reader.skip();
        }
        String result = reader.getString().substring(start, reader.getCursor());
        //if (!(result.matches("^k[a-z0-9]{9}$") || result.matches("^(?:([a-z0-9-_]{1,32})@)?([a-z0-9]{1,64})\\.kro$"))) {
        //    throw new SimpleCommandExceptionType(new LiteralMessage("Not a valid address or (meta)name")).createWithContext(reader);
        //}
        return result;
    }

    private AddressArgumentType() {}

    public static AddressArgumentType address() {
        return new AddressArgumentType();
    }

    public static String getAddress(final CommandContext<?> context, final String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
