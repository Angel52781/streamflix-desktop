package dev.streamflix.desktop;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Build-time exporter for the Windows application icon. */
public final class IconExporter {
    private static final int[] SIZES = {16, 20, 24, 32, 48, 64, 128, 256};

    private IconExporter() {}

    public static void main(String[] args) throws Exception {
        Path out = args.length > 0 ? Path.of(args[0]) : Path.of("build", "branding");
        Files.createDirectories(out);
        for (int size : SIZES) {
            ImageIO.write(BrandMark.appIconImage(size), "png",
                    out.resolve("streamflix-" + size + ".png").toFile());
        }
        writeIco(out.resolve("StreamflixDesktop.ico"));
        ImageIO.write(BrandMark.appIconImage(1024), "png",
                out.resolve("streamflix-app-icon-1024.png").toFile());
        ImageIO.write(BrandMark.markImage(1024), "png",
                out.resolve("streamflix-mark-1024.png").toFile());
    }

    static void writeIco(Path path) throws IOException {
        List<byte[]> images = new ArrayList<>(SIZES.length);
        for (int size : SIZES) {
            BufferedImage image = BrandMark.appIconImage(size);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            images.add(png.toByteArray());
        }

        int directorySize = 6 + 16 * images.size();
        int offset = directorySize;
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))) {
            writeLeShort(out, 0);
            writeLeShort(out, 1);
            writeLeShort(out, images.size());

            for (int i = 0; i < images.size(); i++) {
                int size = SIZES[i];
                out.writeByte(size >= 256 ? 0 : size);
                out.writeByte(size >= 256 ? 0 : size);
                out.writeByte(0);
                out.writeByte(0);
                writeLeShort(out, 1);
                writeLeShort(out, 32);
                writeLeInt(out, images.get(i).length);
                writeLeInt(out, offset);
                offset += images.get(i).length;
            }
            for (byte[] image : images) out.write(image);
        }
    }

    private static void writeLeShort(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xff);
        out.writeByte((value >>> 8) & 0xff);
    }

    private static void writeLeInt(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xff);
        out.writeByte((value >>> 8) & 0xff);
        out.writeByte((value >>> 16) & 0xff);
        out.writeByte((value >>> 24) & 0xff);
    }
}
