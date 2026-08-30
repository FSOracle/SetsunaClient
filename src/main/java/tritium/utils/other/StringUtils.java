package tritium.utils.other;

import java.util.regex.Pattern;

public class StringUtils {
    public static String removeFormattingCodes(String text) {
        return Pattern.compile("(?i)" + '§' + "[0-9A-FK-OR]").matcher(text).replaceAll("");
    }

    public static String returnEmptyStringIfNull(String input) {
        return input == null ? "" : input;
    }

}