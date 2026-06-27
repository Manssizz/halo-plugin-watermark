package run.halo.watermark;

import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ImageWriterSpi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Slf4j
@Component
public class WatermarkPlugin extends BasePlugin {

    public WatermarkPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    public void start() {
        registerWebpSpi();
        log.info("Image Watermark & WebP plugin started");
    }

    @Override
    public void stop() {
        deregisterWebpSpi();
        log.info("Image Watermark & WebP plugin stopped");
    }

    /**
     * Manually register sejda webp-imageio SPI with ImageIO.
     *
     * PF4J loads plugin JARs with its own PluginClassLoader, but Java's
     * ImageIO uses the system ClassLoader for ServiceLoader discovery.
     * So ImageIO.getImageWritersByMIMEType("image/webp") finds nothing
     * unless we register the SPI explicitly.
     */
    private void registerWebpSpi() {
        try {
            var registry = IIORegistry.getDefaultInstance();

            var writerSpi = new com.luciad.imageio.webp.WebPImageWriterSpi();
            registry.registerServiceProvider(writerSpi, ImageWriterSpi.class);
            log.info("WebP ImageWriter SPI registered");

            var readerSpi = new com.luciad.imageio.webp.WebPImageReaderSpi();
            registry.registerServiceProvider(readerSpi, ImageReaderSpi.class);
            log.info("WebP ImageReader SPI registered");
        } catch (Throwable e) {
            log.warn("Failed to register WebP SPI: {} - {}",
                e.getClass().getSimpleName(), e.getMessage());
            log.warn("WebP encoding will be unavailable; JPEG fallback will be used");
        }
    }

    private void deregisterWebpSpi() {
        try {
            var registry = IIORegistry.getDefaultInstance();

            var writerSpi = registry.getServiceProviderByClass(
                com.luciad.imageio.webp.WebPImageWriterSpi.class);
            if (writerSpi != null) {
                registry.deregisterServiceProvider(writerSpi, ImageWriterSpi.class);
            }

            var readerSpi = registry.getServiceProviderByClass(
                com.luciad.imageio.webp.WebPImageReaderSpi.class);
            if (readerSpi != null) {
                registry.deregisterServiceProvider(readerSpi, ImageReaderSpi.class);
            }
        } catch (Throwable ignored) {
        }
    }
}
