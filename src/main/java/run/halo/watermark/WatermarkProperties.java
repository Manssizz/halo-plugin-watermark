package run.halo.watermark;

import lombok.Data;

/**
 * POJO representing watermark plugin settings.
 * Maps to the "basic" group in settings.yaml via ReactiveSettingFetcher.
 */
@Data
public class WatermarkProperties {

    private boolean enabled = true;
    private boolean enableWebpConversion = true;
    private String watermarkText = "halo.dev";
    private String fontSizeMode = "PERCENTAGE";
    private double fontSize = 2;
    private String position = "BOTTOM_RIGHT";
    private int opacity = 60;
    private double margin = 2;
    private int quality = 80;
    private String fontFamily = "SansSerif";
    private String fontStyle = "BOLD";
    private String fontColor = "#FFFFFF";
    private boolean enableShadow = true;

    public WatermarkPosition getPositionEnum() {
        try {
            return WatermarkPosition.valueOf(position);
        } catch (IllegalArgumentException e) {
            return WatermarkPosition.BOTTOM_RIGHT;
        }
    }

    public FontSizeMode getFontSizeModeEnum() {
        try {
            return FontSizeMode.valueOf(fontSizeMode);
        } catch (IllegalArgumentException e) {
            return FontSizeMode.PERCENTAGE;
        }
    }

    public int getFontStyleInt() {
        return switch (fontStyle) {
            case "BOLD" -> java.awt.Font.BOLD;
            case "ITALIC" -> java.awt.Font.ITALIC;
            case "BOLD_ITALIC" -> java.awt.Font.BOLD | java.awt.Font.ITALIC;
            default -> java.awt.Font.PLAIN;
        };
    }

    public java.awt.Color getParsedFontColor() {
        try {
            return java.awt.Color.decode(fontColor);
        } catch (NumberFormatException e) {
            return java.awt.Color.WHITE;
        }
    }

    public enum FontSizeMode {
        PERCENTAGE,
        PIXEL
    }

    public enum WatermarkPosition {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        CENTER_LEFT,
        CENTER,
        CENTER_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    }
}
