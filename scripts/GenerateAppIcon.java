import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Renders the supplied SVG's existing PNG layers; no redesign or external assets. */
class GenerateAppIcon {
    public static void main(String[] args) throws Exception {
        Path brand = Path.of("src/main/resources/brand");
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        byte[][] frames = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            var canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            var graphics = canvas.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.scale(size / 1000.0, size / 1000.0);
                graphics.drawImage(ImageIO.read(brand.resolve("_Image1.png").toFile()), 0, 0, 1000, 1000, null);
                var transform = new AffineTransform();
                transform.translate(-454.305768, -430.892967);
                transform.scale(1.873024, 1.873024);
                graphics.transform(transform);
                graphics.drawImage(ImageIO.read(brand.resolve("_Image2.png").toFile()), 444, 431, 241, 214, null);
                graphics.drawImage(ImageIO.read(brand.resolve("_Image3.png").toFile()), 334, 332, 348, 330, null);
            } finally { graphics.dispose(); }
            var png = new ByteArrayOutputStream();
            ImageIO.write(canvas, "png", png);
            frames[i] = png.toByteArray();
            if (size == 256) Files.write(brand.resolve("app-icon.png"), frames[i]);
        }
        int offset = 6 + sizes.length * 16;
        var header = ByteBuffer.allocate(offset).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short)0).putShort((short)1).putShort((short)sizes.length);
        for (int i = 0; i < sizes.length; i++) {
            header.put((byte)sizes[i]).put((byte)sizes[i]).put((byte)0).put((byte)0);
            header.putShort((short)1).putShort((short)32).putInt(frames[i].length).putInt(offset);
            offset += frames[i].length;
        }
        var ico = new ByteArrayOutputStream();
        ico.write(header.array());
        for (byte[] frame : frames) ico.write(frame);
        Files.write(brand.resolve("app-icon.ico"), ico.toByteArray());
        System.out.println("Generated PNG and seven-resolution Windows ICO from the supplied logo.");
    }
}
