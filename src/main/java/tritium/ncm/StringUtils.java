package tritium.ncm;

import lombok.experimental.UtilityClass;

/**
 * @author IzumiiKonata
 * Date: 2025/7/2 19:54
 */
@UtilityClass
public class StringUtils {

    public boolean isBlank(CharSequence cs) {
        int strLen;
        if (cs != null && (strLen = cs.length()) != 0) {
            for(int i = 0; i < strLen; ++i) {
                if (!Character.isWhitespace(cs.charAt(i))) {
                    return false;
                }
            }

            return true;
        } else {
            return true;
        }
    }

    public boolean isNotBlank(CharSequence cs) {
        return !isBlank(cs);
    }

}
