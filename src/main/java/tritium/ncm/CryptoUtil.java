package tritium.ncm;

import lombok.Data;
import tritium.ncm.math.DigestUtils;
import tritium.ncm.math.Hex;
import tritium.utils.json.JsonUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 网易云音乐API加密工具类
 */
public class CryptoUtil {
    
    private static final String IV = "0102030405060708";
    private static final String PRESET_KEY = "0CoJUm6Qyw8W8jud";
    private static final String LINUX_API_KEY = "rFgB&h#%2?^eDg:Q";
    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String EAPI_KEY = "e82ckenh8dichen8";
    
    private static final String PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB";
    
    private static final SecureRandom random = new SecureRandom();
    
    @Data
    public static class WeapiResult {
        private String params;
        private String encSecKey;
    }
    
    @Data
    public static class LinuxapiResult {
        private String eparams;
    }
    
    @Data
    public static class EapiResult {
        private String params;
    }
    
    @Data
    public static class DecryptResult {
        private String url;
        private Object data;
    }
    
    /**
     * AES加密
     */
    public static String aesEncrypt(String text, String mode, String key, String iv, String format) {
        try {
            Cipher cipher = Cipher.getInstance("AES/" + mode.toUpperCase() + "/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            
            if ("CBC".equalsIgnoreCase(mode)) {
                IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            }
            
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            
            if ("base64".equals(format)) {
                return Base64.getEncoder().encodeToString(encrypted);
            } else {
                return Hex.encodeHexString(encrypted).toUpperCase();
            }
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败", e);
        }
    }
    
    public static String aesEncrypt(String text, String mode, String key, String iv) {
        return aesEncrypt(text, mode, key, iv, "base64");
    }
    
    /**
     * AES解密
     */
    public static String aesDecrypt(String ciphertext, String key, String iv, String format) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            
            byte[] encrypted;
            if ("base64".equals(format)) {
                encrypted = Base64.getDecoder().decode(ciphertext);
            } else {
                encrypted = Hex.decodeHex(ciphertext);
            }
            
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES解密失败", e);
        }
    }
    
    public static String aesDecrypt(String ciphertext, String key, String iv) {
        return aesDecrypt(ciphertext, key, iv, "base64");
    }
    
    /**
     * RSA加密
     */
    public static String rsaEncrypt(String str, String publicKeyStr) {
        try {
            // 移除PEM格式的头尾标识

            byte[] keyBytes = Base64.getDecoder().decode(publicKeyStr);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            
            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(str.getBytes(StandardCharsets.UTF_8));
            
            return Hex.encodeHexString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("RSA加密失败", e);
        }
    }
    
    /**
     * weapi加密
     */
    public static WeapiResult weapi(Object data) {
        String text = JsonUtils.toJsonString(data);

        StringBuilder secretKey = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            secretKey.append(BASE62.charAt(random.nextInt(62)));
        }
        
        String firstEncrypt = aesEncrypt(text, "cbc", PRESET_KEY, IV);
        String params = aesEncrypt(firstEncrypt, "cbc", secretKey.toString(), IV);
        
        String reversedKey = new StringBuilder(secretKey.toString()).reverse().toString();
        String encSecKey = rsaEncrypt(reversedKey, PUBLIC_KEY);
        
        WeapiResult result = new WeapiResult();
        result.setParams(params);
        result.setEncSecKey(encSecKey);
        return result;
    }
    
    /**
     * linuxapi加密
     */
    public static LinuxapiResult linuxapi(Object data) {
        String text = JsonUtils.toJsonString(data);
        String eparams = aesEncrypt(text, "ecb", LINUX_API_KEY, "", "hex");
        
        LinuxapiResult result = new LinuxapiResult();
        result.setEparams(eparams);
        return result;
    }
    
    /**
     * eapi加密
     */
    public static EapiResult eapi(String url, Object data) {
        String text = data instanceof String ? (String) data : JsonUtils.toJsonString(data);
        String message = "nobody" + url + "use" + text + "md5forencrypt";
        String digest = DigestUtils.md5Hex(message);
        String dataStr = url + "-36cd479b6b5-" + text + "-36cd479b6b5-" + digest;
        String params = aesEncrypt(dataStr, "ecb", EAPI_KEY, "", "hex");
        
        EapiResult result = new EapiResult();
        result.setParams(params);
        return result;
    }
    
    /**
     * eapi响应解密
     */
    public static Object eapiResDecrypt(String encryptedParams) {
        String decryptedData = aesDecrypt(encryptedParams, EAPI_KEY, "", "hex");
        return JsonUtils.parse(decryptedData, Object.class);
    }
    
    /**
     * eapi请求解密
     */
    public static DecryptResult eapiReqDecrypt(String encryptedParams) {
        String decryptedData = aesDecrypt(encryptedParams, EAPI_KEY, "", "hex");
        
        String pattern = "(.*?)-36cd479b6b5-(.*?)-36cd479b6b5-(.*)";
        java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher matcher = regex.matcher(decryptedData);
        
        if (matcher.find()) {
            String url = matcher.group(1);
            String dataJson = matcher.group(2);
            Object data = JsonUtils.parse(dataJson, Object.class);
            
            DecryptResult result = new DecryptResult();
            result.setUrl(url);
            result.setData(data);
            return result;
        }
        
        return null;
    }
    
    /**
     * 通用解密方法
     */
    public static String decrypt(String cipher) {
        try {
            Cipher decryptCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(EAPI_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            decryptCipher.init(Cipher.DECRYPT_MODE, keySpec);
            
            byte[] encrypted = Hex.decodeHex(cipher);
            byte[] decrypted = decryptCipher.doFinal(encrypted);
            
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
}