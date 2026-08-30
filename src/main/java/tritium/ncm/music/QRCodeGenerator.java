package tritium.ncm.music;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.common.BitMatrix;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Hashtable;
import javax.imageio.ImageIO;

public class QRCodeGenerator {

    private static volatile BufferedImage qrImage;
    private static volatile byte[] qrPng = new byte[0];
    private static volatile String lastAddress = "";

    private QRCodeGenerator() {
    }

    public static boolean generateAndLoadTexture(String address) {
        try {
            BufferedImage image = generateQRCode(address, 320, 320);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("PNG encoder is unavailable");
            }
            qrImage = image;
            qrPng = output.toByteArray();
            lastAddress = address;
            return true;
        } catch (Exception e) {
            clear();
            return false;
        }
    }

    public static boolean loadPng(String key, byte[] png) {
        if (key == null || key.isBlank() || png == null || png.length == 0) {
            clear();
            return false;
        }
        try {
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (image == null) {
                clear();
                return false;
            }
            qrImage = image;
            qrPng = png.clone();
            lastAddress = key;
            return true;
        } catch (Exception error) {
            clear();
            return false;
        }
    }

    public static BufferedImage getQrImage() {
        return qrImage;
    }

    public static String getLastAddress() {
        return lastAddress;
    }

    public static byte[] getQrPng() {
        return qrPng.clone();
    }

    public static void clear() {
        lastAddress = "";
        qrPng = new byte[0];
        qrImage = null;
    }

    public static BufferedImage generateQRCode(String text, int width, int height) throws Exception {
        Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
        hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
        hints.put(EncodeHintType.MARGIN, 1);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return image;
    }
}
