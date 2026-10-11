import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public final class GenerateCape {
    public static void main(String[] args) throws Exception {
        BufferedImage logo = ImageIO.read(new File(args[0]));
        BufferedImage cape = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = cape.createGraphics();
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(0, 0, 64, 64);
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        // Minecraft 26.1 PlayerCapeModel samples the cape body from texOffs(0, 0)
        // with a 10x16 rectangle. Keep the full source logo in that rectangle.
        graphics.drawImage(logo, 0, 0, 10, 16, null);
        graphics.dispose();
        ImageIO.write(cape, "png", new File(args[1]));
    }
}
