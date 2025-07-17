package cc.reconnected.kromer;

import eu.pb4.placeholders.api.TextParserUtils;
import net.minecraft.text.Text;

public class Locale {

    public enum Messages {
        KROMER_UNAVAILABLE("<red>Kromer is currently unavailable."),
        NO_WALLET("<red>You do not have a wallet. This should be impossible. Rejoin/contact a staff member."),
        BALANCE("<green>Your balance is: <dark_green>%.2fKRO."),
        VERSION("<yellow>Fabric version: <gold>%s</gold>, server version: <gold>%s</gold>"),
        KROMER_INFORMATION("""
            <green>Your kromer information:
            Address: <dark_green><click:copy_to_clipboard:%s>%s</click></dark_green> <gray>(click to copy!)</gray>
            Private key: <dark_green><click:copy_to_clipboard:%s>%s</click></dark_green> <gray>(click to copy!)</gray> </green>"""),
        ADDED_KRO("<green>Added <dark_green>%.2fKRO</dark_green> to %s.</green>"),
        WELFARE_NOT_MUTED("<green>Welfare notifications are no longer muted."),
        WELFARE_MUTED("<red>Welfare notifications are now muted."),
        NO_PENDING("<red>No pending payment to confirm."),
        ERROR("<red>Error while contacting kromer: %s"),
        PAYMENT_CONFIRMED("<green>Payment of <dark_green>%.2fKRO</dark_green> to <dark_green>%s</dark_green> confirmed.</green>"),
        RETROACTIVE("<green>You have received: <dark_green>%.2fKRO</dark_green> for your playtime!</green>"),
        WELFARE_GIVEN("<gray>Thanks for playing! You've been given your welfare of %.2fKRO!"),
        NOTIFY_TRANSFER("<green>You have been sent <dark_green>%.2fKRO</dark_green>, from <dark_green>%s</dark_green>."),
        OUTGOING_NOT_SEEN("<red>From your account, <dark_red>%.2fKRO</dark_red>, has been sent to <dark_red>%s</dark_red>. Executed at: %s.</red>"),
        INCOMING_NOT_SEEN("<green><dark_green>%s</dark_green> deposited <dark_green>%.2fKRO</dark_green> into your account. Executed at %s.</green>");

        private final String template;

        Messages(String template) {
            this.template = template;
        }

        public String raw(Object... args) {
            return String.format(java.util.Locale.US, template, args);
        }

        public Text asText(Object... args) {
            return TextParserUtils.formatText(raw(args));
        }
    }

    public static Text use(Messages message, Object... args) {
        return message.asText(args);
    }
}

