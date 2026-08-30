package tritium.ncm;

import lombok.experimental.UtilityClass;

/**
 * @author IzumiiKonata
 * Date: 2025/7/2 20:00
 */
@UtilityClass
public class OptionsUtil {

    private String COOKIE = "";

    public void setCookie(String cookie) {
        COOKIE = cookie;
    }

    public static String getCookie() {
        return COOKIE;
    }

    public RequestUtil.RequestOptions createOptions() {
        return createOptions("");
    }

    public RequestUtil.RequestOptions createOptions(String crypto) {
        return RequestUtil.RequestOptions.builder()
                .crypto(crypto)
                .cookie(COOKIE)
                .ua("")
                .proxy("")
//                .realIP("123.168.116.9")
                .encryptedResponse(null)
                .build();
    }

}
