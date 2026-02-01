package cc.reconnected.kromer;

import eu.pb4.placeholders.api.TextParserUtils;
import eu.pb4.placeholders.api.node.LiteralNode;
import eu.pb4.placeholders.api.node.parent.ParentNode;
import eu.pb4.placeholders.api.parsers.TextParserV1;
import net.minecraft.network.chat.Component;

public class Locale {

    public enum Messages {
        KROMER_UNAVAILABLE("<red>Kromer is currently unavailable."),
        NO_WALLET(
            "<red>You do not have a wallet. This should be impossible. Rejoin/contact a staff member."
        ),
        BALANCE("<green>Your balance is: <dark_green>%.2fKRO."),
        BALANCE_OTHERS("<green>%s's balance is: <dark_green>%.2fKRO."),

        VERSION(
            "<yellow>Fabric version: <gold>%s</gold>, server version: <gold>%s</gold>"
        ),
        KROMER_INFORMATION(
            """
            <green>Your kromer information:
            Address: <dark_green><click:copy_to_clipboard:%s>%s</click></dark_green> <gray>(click to copy!)</gray>
            Private key: <dark_green><click:copy_to_clipboard:%s>%s</click></dark_green> <gray>(click to copy!)</gray>

            <red> Your private key is PRIVATE information. It is used to access all of your kromer. Do not share it with anyone.</red>"""),
        ADDED_KRO(
            "<green>Added <dark_green>%.2fKRO</dark_green> to %s.</green>"
        ),
        WELFARE_NOT_MUTED("<green>Welfare notifications are no longer muted."),
        WELFARE_MUTED("<red>Welfare notifications are now muted."),
        NO_PENDING("<red>No pending payment to confirm."),
        NOT_ONLINE("<red>You must be online to use this command."),
        ERROR("<red>Error: %s"),
        PAYMENT_RECIPIENT_NOT_FOUND(
            "<red>User not found and not a valid address."
        ),
        PAYMENT_RECIPIENT_NO_WALLET(
            "<red>Other user does not have a wallet. They haven't joined recently."
        ),
        PAYMENT_SENDER_NO_WALLET(
            "<red>You do not have a wallet. This should be impossible. Rejoin/contact a staff member."
        ),
        PAYMENT_CONFIRMATION(
            "<green>Are you sure you want to send <dark_green>%.2fKRO</dark_green> to <dark_green>%s</dark_green>?</green> "
        ),
        PAYMENT_CONFIRMATION_BUTTON(
            "<bold><green><click:run_command:'/confirm_pay'><hover:show_text:'Click to confirm payment of %.2fKRO to %s'>[CONFIRM]</hover></click></green></bold>"
        ),
        PAYMENT_CONFIRMED(
            "<green>Payment of <dark_green>%.2fKRO</dark_green> to <dark_green>%s</dark_green> confirmed.</green>"
        ),
        RETROACTIVE(
            "<green>You have received: <dark_green>%.2fKRO</dark_green> for your playtime!</green>"
        ),
        WELFARE_GIVEN(
            "<gray>Thanks for playing! You've been given your welfare of %.2fKRO!"
        ),
        NOTIFY_TRANSFER(
            "<green>You have been sent <dark_green>%.2fKRO</dark_green>, from <dark_green>%s</dark_green>."
        ),
        NOTIFY_TRANSFER_MESSAGE(
            "<green>You have been sent <dark_green>%.2fKRO</dark_green>, from <dark_green>%s</dark_green>, with message: \"%s\"."
        ),
        OUTGOING_NOT_SEEN(
            "<red>From your account, <dark_red>%.2fKRO</dark_red>, has been sent to <dark_red>%s</dark_red>. Executed at: %s.</red>"
        ),
        INCOMING_NOT_SEEN(
            "<green><dark_green>%s</dark_green> deposited <dark_green>%.2fKRO</dark_green> into your account. Executed at %s.</green>"
        ),
        OPTED_IN_WELFARE("<green>Opted into welfare."),
        ALREADY_OPTED_IN("<red>You're already opted in to welfare."),
        ALREADY_OPTED_OUT("<red>You have already opted out of welfare."),
        OPTOUT_WARNING("""
            <red>Opting out of welfare means all of your kromer will be removed, including all
            starting kromer, future welfare payments, and kromer you have been sent.
            Are you sure you want to opt out of welfare?</red>"""),
        OPTOUT_INFO("""
            <yellow>INFO: You are able to opt in back to welfare, but you will not regain your starting kromer.</yellow>"""),
        OPTED_OUT_WELFARE("<red>You have opted out of welfare."),
        CONFIRM_OPTOUT_BUTTON("<bold><red><click:run_command:'/kromer optOut confirm'>[CONFIRM]</click></red></bold>"),
        TRANSACTIONS_INFO("<green>Transactions for %s on page %s"),
        TRANSACTIONS_EMPTY("<red>No transactions found<reset>"),
        TRANSACTION("%s%s: #%s, %s->%s: %.2f KRO, Metadata: '%s'<reset>");

        private final String template;

        Messages(String template) {
            this.template = template;
        }

        public String raw(Object... args) {
            return String.format(java.util.Locale.US, template, args);
        }

        public Component asText(Object... args) {
            return TextParserUtils.formatText(raw(args));
        }
        public Component asSafeText(Object... args) {
            return (new ParentNode(TextParserV1.SAFE.parseNodes(new LiteralNode(raw(args))))).toText(null, true);
        }
    }

    public static Component use(Messages message, Object... args) {
        return message.asText(args);
    }
    public static Component useSafe(Messages message, Object... args) {
        return message.asSafeText(args);
    }
}
