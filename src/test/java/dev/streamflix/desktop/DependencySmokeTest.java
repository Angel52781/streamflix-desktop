package dev.streamflix.desktop;

import javax.imageio.ImageIO;
import org.jsoup.Jsoup;

/** Runs against the built JAR, so its manifest must resolve sibling libraries. */
public final class DependencySmokeTest {
    public static void main(String[] args) {
        if (!"ready".equals(Jsoup.parse("<p>ready</p>").selectFirst("p").text())) {
            throw new AssertionError("Jsoup unavailable");
        }
        if (!ImageIO.getImageReadersByFormatName("WebP").hasNext()) {
            throw new AssertionError("ImageIO WebP service unavailable");
        }
        System.out.println("DependencySmokeTest OK");
    }
}
