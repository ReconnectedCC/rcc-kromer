package cc.reconnected.kromer;

import eu.pb4.placeholders.api.node.LiteralNode;
import eu.pb4.placeholders.api.node.parent.ParentNode;
import eu.pb4.placeholders.api.parsers.TagParser;
import me.alexdevs.solstice.api.text.Format;
import net.minecraft.network.chat.Component;
import ovh.sad.jkromer.Errors;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static java.util.Map.entry;

public class Locale {
    public static final String BUTTON_FORMAT = "<click:copy_to_clipboard:'${content}'><hover:show_text:'${hoverText}'><aqua>[</aqua>${label}<aqua>]</aqua></hover></click>";

    private static final Map<String, String> ERROR_MESSAGES = Map.ofEntries(
            entry("invalid_parameter",    "Invalid parameter provided."),
            entry("address_not_found",    "Wallet address not found."),
            entry("name_taken",           "This name is already taken."),
            entry("insufficient_funds",   "Insufficient balance for this transaction."),
            entry("not_name_owner",       "You don't own this name."),
            entry("name_not_found",       "Name does not exist in the system."),
            entry("internal_problem",     "Internal server error – please try again later."),
            entry("same_wallet_transfer", "Cannot transfer funds to the same wallet.")
    );

    public static Component parse(Messages message) {
        return Format.parse(message.template);
    }

    public static Component parse(Messages message, Map<String, Component> placeholders) {
        return Format.parse(message.template, placeholders);
    }

    public static Component parse(Messages message, BigDecimal amount) {
        var amountString = String.format("%.2f", amount);
        return parse(message, Map.of("amount", Component.literal(amountString)));
    }

    public static Component parse(Messages message, BigDecimal amount, Map<String, Component> placeholders) {
        var map = new HashMap<String, Component>();
        var amountString = String.format("%.2f", amount);
        map.put("amount", Component.literal(amountString));
        map.putAll(placeholders);
        return parse(message, map);
    }

    public static Component error(Throwable error) {
        return parse(Locale.Messages.ERROR, Map.of("error", Component.literal(error.getMessage())));
    }

    public static Component error(Errors.ErrorResponse error) {
        String code = error.error();
        String pretty = ERROR_MESSAGES.getOrDefault(code, "An unexpected error occurred.");
        return parse(Locale.Messages.ERROR, Map.of("error", Component.literal(pretty)));
    }

    public static Component buttonCopy(Component label, Component hoverText, String content) {
        var placeholders = Map.of(
                "label", label,
                "hoverText", hoverText,
                "content", Component.nullToEmpty(content)
        );

        var text = Format.parse(BUTTON_FORMAT);
        return Format.parse(text, placeholders);
    }

    public static Component buttonCopy(String label, String hoverText, String content) {
        var placeholders = Map.of(
                "label", Component.literal(label),
                "hoverText", Component.literal(hoverText),
                "content", Component.nullToEmpty(content)
        );

        var text = Format.parse(BUTTON_FORMAT);
        return Format.parse(text, placeholders);
    }

    public enum Messages {
        KROMER_UNAVAILABLE("<red>Kromer is currently unavailable.</red>"),
        NO_OWN_WALLET(
                "<red>There has been an error retrieving your wallet. Rejoin or contact a staff member if the problem persists.</red>"
        ),
        BALANCE("<gold>Your balance is <green>${amount} KRO</green>."),
        BALANCE_OTHERS("<gold><yellow>${address}</yellow>'s balance is <green>${amount} KRO</green>."),
        VERSION(
                "<gold>Plugin version: <yellow>${pluginVersion}</yellow> - Kromer version: <yellow>${kromerVersion}</yellow></gold>"
        ),
        KROMER_INFORMATION(
                """
                        <gold>Your wallet information:
                        Address: <yellow><hover:Click to copy><click:copy_to_clipboard:${address}>${address}</click></hover></yellow>
                        Private key: ${privateKeyButton}
                        </gold>
                        <red>Caution! Your private key gives full access to your wallet. Do not share it with anyone.</red>"""),
        ADDED_KRO(
                "<gold>Added <yellow>${amount} KRO</yellow> to <yellow>${player}</yellow>.</gold>"
        ),
        WELFARE_NOT_MUTED("<green>Welfare notifications are no longer muted.</green>"),
        WELFARE_MUTED("<gold>Welfare notifications are now muted.</gold>"),
        NO_PENDING("<gold>No pending payment to confirm.</gold>"),
        ERROR("<red>Error: ${error}</red>"),
        USER_OR_ADDRESS_NOT_FOUND(
                "<gold>User not found or not a valid address.</gold>"
        ),
        OTHER_USER_NO_WALLET(
                "<gold>Other user does not yet have a wallet.</gold>"
        ),
        PAYMENT_CONFIRMATION(
                "<gold>Are you sure you want to send <yellow>${amount} KRO</yellow> to <yellow>${recipient}</yellow>?\n\n${confirmButton}</gold>"
        ),
        PAYMENT_CONFIRMED(
                "<green>Payment of <yellow>${amount} KRO</yellow> to <yellow>${recipient}</yellow> confirmed.</green>"
        ),
        RETROACTIVE(
                "<green>You have received: <yellow>${amount} KRO</yellow> for your playtime!</green>"
        ),
        WELFARE_GIVEN(
                "<gray>Thanks for playing! You've been given your welfare of ${amount} KRO!"
        ),
        NOTIFY_TRANSFER(
                "<green><yellow>${sender}</yellow> has sent you <yellow>${amount} KRO</yellow>.</green>"
        ),
        NOTIFY_TRANSFER_MESSAGE(
                "<green><yellow>${sender}</yellow> has sent you <yellow>${amount} KRO</yellow> with the message: <gray>${message}</gray>.</green>"
        ),
        NOTIFY_TRANSFER_MESSAGE_ERROR(
                "<gold><yellow>${sender}</yellow> has sent you <yellow>${amount} KRO</yellow> with the error: <red>${message}</red>.</gold>"
        ),
        OUTGOING_NOT_SEEN(
                "<gold><yellow>${amount}</yellow> have been sent to <yellow>${recipient}</yellow> at <yellow>${date}</yellow>.</gold>"
        ),
        INCOMING_NOT_SEEN(
                "<green><yellow>${sender}</yellow> deposited <yellow>${amount} KRO</yellow> at <yellow>${date}</yellow>.</green>"
        ),
        OPTED_IN_WELFARE("<green>Opted into welfare.</green>"),
        ALREADY_OPTED_IN("<green>You're already opted in to welfare.</green>"),
        ALREADY_OPTED_OUT("<gold>You have already opted out of welfare.</gold>"),
        OPTOUT_WARNING("<gold>Opting out of welfare means all of your KRO will be permanently removed, including all starting KRO, past welfare payments, and KRO you have been sent.\nAre you sure you want to opt out of welfare?</gold>\n\n${confirmButton}"),
        OPTED_OUT_WELFARE("<gold>You have opted out of welfare.</gold>"),
        TRANSACTIONS_INFO("<gold>Transactions for <yellow>${player}</yellow> on page <gray>${page}</gray>:</gold>"),
        TRANSACTIONS_EMPTY("<gold>No transactions found.</gold>"),
        TRANSACTION_IN(" <gold><gray><hover:show_text:'${datetime}'>[${date}]</hover></gray> <aqua><hover:show_text:'${type}'>#${id}</hover></aqua>: <yellow>${sender}</yellow> → <green>${amount} KRO</green>.</gold>"),
        TRANSACTION_OUT(" <gold><gray><hover:show_text:'${datetime}'>[${date}]</hover></gray> <aqua><hover:show_text:'${type}'>#${id}</hover></aqua>: <red>${amount} KRO</red> → <yellow>${recipient}</yellow>.</gold>"),
        TRANSACTION_MINED(" <gold><gray><hover:show_text:'${datetime}'>[${date}]</hover></gray> <aqua><hover:show_text:'${type}'>#${id}</hover></aqua>: ⛏ <gray>${amount} KRO</gray>.</gold>"),
        TRANSACTION_OTHER(" <gold><gray><hover:show_text:'${datetime}'>[${date}]</hover></gray> <aqua><hover:show_text:'${type}'>#${id}</hover></aqua>: <yellow>${sender}</yellow> → <gray>${amount} KRO</gray> → <yellow>${recipient}</yellow>.</gold>"),
        TRANSACTION_METADATA("\n<gray>${metadata}</gray>"),
        SAME_WALLET_TRANSFER("<gold>You cannot send KRO to yourself.</gold>")
        ;

        private final String template;

        Messages(String template) {
            this.template = template;
        }

        public String raw(Object... args) {
            return String.format(java.util.Locale.ROOT, template, args);
        }

        public Component asText(Object... args) {
            return TagParser.SIMPLIFIED_TEXT_FORMAT.parseNode(raw(args)).toText();
        }

        public Component asSafeText(Object... args) {
            return (new ParentNode(TagParser.SIMPLIFIED_TEXT_FORMAT_SAFE.parseNodes(new LiteralNode(raw(args))))).toText(null, true);
        }
    }
}
