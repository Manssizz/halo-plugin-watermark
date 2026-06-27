package run.halo.watermark;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import run.halo.watermark.WatermarkProperties.FontSizeMode;
import run.halo.watermark.WatermarkProperties.WatermarkPosition;

/**
 * Pure-Java image processor for watermark rendering and format conversion.
 * Uses Java2D for watermark, sejda webp-imageio for WebP encoding.
 */
@Slf4j
@UtilityClass
public class ImageProcessor {

    private static final int MIN_FONT_SIZE = 10;
    private static final int MAX_FONT_SIZE = 500;

    /**
     * Read image bytes into BufferedImage.
     * Returns null if the bytes cannot be decoded as an image.
     */
    public static BufferedImage readImage(byte[] imageBytes) throws IOException {
        try (var bais = new ByteArrayInputStream(imageBytes)) {
            var image = ImageIO.read(bais);
            if (image == null) {
                return null;
            }
            // Ensure image is in a writable color model (some formats return read-only)
            if (image.getType() == BufferedImage.TYPE_CUSTOM || image.getType() == 0) {
                var converted = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                var g = converted.createGraphics();
                g.drawImage(image, 0, 0, null);
                g.dispose();
                return converted;
            }
            return image;
        }
    }

    /**
     * Apply text watermark to image using the given properties.
     * Handles adaptive font sizing based on image dimensions.
     */
    public static BufferedImage applyWatermark(BufferedImage original, WatermarkProperties props) {
        if (props.getWatermarkText() == null || props.getWatermarkText().isBlank()) {
            return original;
        }

        int width = original.getWidth();
        int height = original.getHeight();

        // Create a copy to draw on (preserve alpha if present)
        var hasAlpha = original.getColorModel().hasAlpha();
        var imageType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        var result = new BufferedImage(width, height, imageType);
        var g2d = result.createGraphics();

        try {
            // Draw original image
            g2d.drawImage(original, 0, 0, null);

            // Enable anti-aliasing for crisp text
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Calculate font size based on mode
            int fontSize = calculateFontSize(width, height, props);
            var font = new Font(props.getFontFamily(), props.getFontStyleInt(), fontSize);
            g2d.setFont(font);

            // Get text dimensions
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(props.getWatermarkText());
            int textHeight = fm.getHeight();
            int textAscent = fm.getAscent();

            // Calculate position
            double diagonal = Math.sqrt((double) width * width + (double) height * height);
            int marginPx = (int) (diagonal * props.getMargin() / 100.0);
            var position = props.getPositionEnum();
            int x = calculateX(position, width, textWidth, marginPx);
            int y = calculateY(position, height, textHeight, textAscent, marginPx);

            // Draw shadow first (if enabled)
            if (props.isEnableShadow()) {
                int shadowOffset = Math.max(1, fontSize / 20);
                g2d.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, (float) (props.getOpacity() / 100.0 * 0.6)));
                g2d.setColor(Color.BLACK);
                g2d.drawString(props.getWatermarkText(), x + shadowOffset, y + shadowOffset);
            }

            // Draw watermark text
            float alpha = (float) (props.getOpacity() / 100.0);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2d.setColor(props.getParsedFontColor());
            g2d.drawString(props.getWatermarkText(), x, y);

        } finally {
            g2d.dispose();
        }

        return result;
    }

    /**
     * Calculate font size with adaptive scaling.
     * Key improvement over the original script's fixed ratio approach:
     * - Percentage mode uses diagonal (sqrt(w² + h²)) as base, giving consistent visual proportion
     * - Clamped to min/max to prevent too small or too large text
     */
    static int calculateFontSize(int width, int height, WatermarkProperties props) {
        int fontSize;
        if (props.getFontSizeModeEnum() == FontSizeMode.PERCENTAGE) {
            double diagonal = Math.sqrt((double) width * width + (double) height * height);
            fontSize = (int) Math.round(diagonal * props.getFontSize() / 100.0);
        } else {
            fontSize = (int) props.getFontSize();
        }
        // Clamp to sensible range
        return Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, fontSize));
    }

    /**
     * Calculate X position based on 9-point anchor system.
     */
    private static int calculateX(WatermarkPosition pos, int imgWidth, int textWidth, int margin) {
        return switch (pos) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> margin;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> (imgWidth - textWidth) / 2;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> imgWidth - textWidth - margin;
        };
    }

    /**
     * Calculate Y position based on 9-point anchor system.
     */
    private static int calculateY(WatermarkPosition pos, int imgHeight,
                                   int textHeight, int textAscent, int margin) {
        return switch (pos) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> margin + textAscent;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> (imgHeight - textHeight) / 2 + textAscent;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> imgHeight - margin - textHeight
                + textAscent;
        };
    }

    /**
     * Encode BufferedImage to WebP format.
     * Falls back to JPEG if WebP encoding fails (native lib unavailable).
     *
     * @return ResultImage containing the encoded bytes, extension, and media type
     */
    public static ResultImage encode(BufferedImage image, WatermarkProperties props) {
        if (props.isEnableWebpConversion()) {
            try {
                byte[] webpBytes = encodeToWebp(image, props.getQuality());
                if (webpBytes != null && webpBytes.length > 0) {
                    log.debug("WebP encoding succeeded, size: {} bytes", webpBytes.length);
                    return new ResultImage(webpBytes, "webp", "image/webp");
                }
            } catch (Exception e) {
                log.warn("WebP encoding failed, falling back to JPEG: {}", e.getMessage());
            }
        }
        // Fallback to JPEG
        try {
            byte[] jpegBytes = encodeToJpeg(image, props.getQuality());
            return new ResultImage(jpegBytes, "jpg", "image/jpeg");
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode image to any format", e);
        }
    }

    /**
     * Encode to WebP using sejda webp-imageio (native lib bundled in JAR).
     */
    static byte[] encodeToWebp(BufferedImage image, int quality) throws IOException {
        // Ensure no alpha channel for lossy WebP (avoids encoding issues)
        BufferedImage rgbImage = image;
        if (image.getColorModel().hasAlpha()) {
            rgbImage = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_INT_RGB);
            var g = rgbImage.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }

        var writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) {
            throw new IOException("No WebP ImageWriter available. "
                + "The sejda webp-imageio native library may not be loaded.");
        }

        ImageWriter writer = writers.next();
        try (var baos = new ByteArrayOutputStream()) {
            try (var ios = new MemoryCacheImageOutputStream(baos)) {
                writer.setOutput(ios);
                var writeParam = writer.getDefaultWriteParam();
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                // sejda webp-imageio: index 0 = Lossy
                writeParam.setCompressionType(writeParam.getCompressionTypes()[0]);
                writeParam.setCompressionQuality(quality / 100.0f);
                writer.write(null, new IIOImage(rgbImage, null, null), writeParam);
            }
            return baos.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    /**
     * Fallback: encode to JPEG with given quality.
     */
    static byte[] encodeToJpeg(BufferedImage image, int quality) throws IOException {
        // JPEG doesn't support alpha — flatten to RGB
        BufferedImage rgbImage = image;
        if (image.getColorModel().hasAlpha()) {
            rgbImage = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_INT_RGB);
            var g = rgbImage.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }

        var writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG ImageWriter available");
        }

        ImageWriter writer = writers.next();
        try (var baos = new ByteArrayOutputStream()) {
            try (var ios = new MemoryCacheImageOutputStream(baos)) {
                writer.setOutput(ios);
                var writeParam = writer.getDefaultWriteParam();
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(quality / 100.0f);
                writer.write(null, new IIOImage(rgbImage, null, null), writeParam);
            }
            return baos.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    /**
     * Result of encoding: raw bytes + metadata for constructing the new FilePart.
     */
    public record ResultImage(byte[] data, String extension, String mediaType) {
    }
}
