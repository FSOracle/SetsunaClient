package tritium.ncm;

import com.sun.jna.platform.win32.Advapi32Util;
import tritium.ncm.api.CloudMusicApi;

import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE;

/**
 * Generate a unique device ID from the device,
 * used for obtaining the anonymous user token for {@link CloudMusicApi#registerAnonimous()}.
 */
public final class DeviceIdGenerator {

    private static final String SALT = "Would you rather watch a tree grow or a knee grow";

    public static String generate() {
        try {
            String fingerprint = collect();
//            System.out.println(fingerprint);

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(SALT.getBytes(StandardCharsets.UTF_8));
            sha256.update(fingerprint.getBytes(StandardCharsets.UTF_8));

            return toHex(sha256.digest()).substring(0, 51);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate device id", e);
        }
    }

    private static String toHex(byte[] data) {
        char[] hex = new char[data.length * 2];
        char[] map = "0123456789ABCDEF".toCharArray();
        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xFF;
            hex[i * 2] = map[v >>> 4];
            hex[i * 2 + 1] = map[v & 0x0F];
        }
        return new String(hex);
    }

    public static String macBytesToHexString(byte[] macBytes) {
        if (macBytes == null || macBytes.length != 6) {
            throw new IllegalArgumentException("macBytes.length != 6");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < macBytes.length; i++) {
            String hex = String.format("%02X", macBytes[i] & 0xFF);
            sb.append(hex);
            if (i < macBytes.length - 1) {
                sb.append(":");
            }
        }
        return sb.toString();
    }

    static String collect() {
        StringBuilder sb = new StringBuilder();

        LinkedHashMap<String, String> properties = new LinkedHashMap<>();

        properties.put("OS", System.getProperty("os.name"));
        properties.put("OSVersion", System.getProperty("os.version"));
        properties.put("Arch", System.getProperty("os.arch"));

        // cpu name
        try {
            String processorNameString = Advapi32Util.registryGetStringValue
                    (HKEY_LOCAL_MACHINE,
                            "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0\\",
                            "ProcessorNameString");

            if (processorNameString != null)
                properties.put("CPU", processorNameString);
        } catch (Exception ignored) {}

        // adapter mac addr
        try {
            NetworkInterface.networkInterfaces().forEach(networkInterface -> {
                try {
                    if (!networkInterface.isUp() || networkInterface.getHardwareAddress() == null || networkInterface.inetAddresses().findAny().isEmpty() || macBytesToHexString(networkInterface.getHardwareAddress()).startsWith("00:50:56"))
                        return;
                    properties.put("AdapterName#" + networkInterface.getIndex(), networkInterface.getDisplayName());
                    properties.put("AdapterMAC#" + networkInterface.getIndex(), macBytesToHexString(networkInterface.getHardwareAddress()));
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {}

        for (Map.Entry<String, String> stringStringEntry : properties.entrySet()) {
            sb.append(stringStringEntry.getKey()).append("=").append(stringStringEntry.getValue()).append("\n");
        }

        return sb.substring(0, sb.length() - 1);
    }
}
