import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.imageio.ImageIO;

/** Source-file tool: java scripts/GenerateAppIcon.java [project-root]. Uses only JDK desktop APIs. */
class GenerateAppIcon {
    public static void main(String[] arguments) throws Exception {
        Path root = arguments.length == 0 ? Path.of("") : Path.of(arguments[0]);
        Path pngDirectory = root.resolve("src/main/resources/com/fhaicl/aidevhub/ui/icons");
        Path ico = root.resolve("packaging/windows/ai-developer-hub.ico");
        Files.createDirectories(pngDirectory);
        Files.createDirectories(ico.getParent());
        int[] sizes = {16, 32, 48, 64, 128, 256};
        var frames = new ArrayList<byte[]>();
        for (int size : sizes) {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.scale(size / 256.0, size / 256.0);
                graphics.setColor(new Color(0x345fc4));
                graphics.fill(new RoundRectangle2D.Double(8, 8, 240, 240, 52, 52));
                graphics.setColor(new Color(0xc9e1ff));
                graphics.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.drawLine(128, 132, 64, 68);
                graphics.drawLine(128, 132, 192, 68);
                graphics.drawLine(128, 132, 128, 202);
                for (int[] node : new int[][]{{64, 68}, {192, 68}, {128, 202}}) {
                    graphics.fill(new Ellipse2D.Double(node[0] - 16, node[1] - 16, 32, 32));
                }
                graphics.setColor(Color.WHITE);
                graphics.fill(new RoundRectangle2D.Double(88, 100, 80, 64, 18, 18));
                graphics.setColor(new Color(0x345fc4));
                graphics.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D code = new Path2D.Double();
                code.moveTo(115, 119); code.lineTo(102, 132); code.lineTo(115, 145);
                code.moveTo(141, 119); code.lineTo(154, 132); code.lineTo(141, 145);
                graphics.draw(code);
            } finally { graphics.dispose(); }
            var output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) throw new IllegalStateException("PNG writer not available");
            byte[] png = output.toByteArray();
            frames.add(png);
            Files.write(pngDirectory.resolve("app-" + size + ".png"), png);
        }
        int offset = 6 + 16 * sizes.length;
        ByteBuffer data = ByteBuffer.allocate(offset + frames.stream().mapToInt(frame -> frame.length).sum())
                .order(ByteOrder.LITTLE_ENDIAN);
        data.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        for (int i = 0; i < sizes.length; i++) {
            int dimension = sizes[i] == 256 ? 0 : sizes[i];
            data.put((byte) dimension).put((byte) dimension).put((byte) 0).put((byte) 0);
            data.putShort((short) 1).putShort((short) 32).putInt(frames.get(i).length).putInt(offset);
            offset += frames.get(i).length;
        }
        frames.forEach(data::put);
        Files.write(ico, data.array());
        System.out.println("Generated application PNG sizes 16-256 and multi-resolution Windows ICO.");
    }
}
