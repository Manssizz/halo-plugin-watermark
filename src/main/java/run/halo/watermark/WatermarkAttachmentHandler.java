package run.halo.watermark;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
import run.halo.app.core.extension.attachment.endpoint.SimpleFilePart;
import run.halo.app.core.extension.attachment.endpoint.UploadOption;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

/**
 * Intercepts image uploads to apply watermark and WebP conversion
 * before delegating to the actual storage handler (Local, S3, etc).
 *
 * <p>Architecture:
 * Halo calls all AttachmentHandler extensions in order via concatMap().next().
 * This handler is @Order(HIGHEST_PRECEDENCE) so it runs FIRST.
 * If the file is an image and watermark is enabled, it:
 * 1. Buffers the image data
 * 2. Applies watermark (Java2D)
 * 3. Converts to WebP (sejda) or JPEG (fallback)
 * 4. Creates a new UploadOption with the processed FilePart
 * 5. Delegates to the remaining handlers (Local/S3) by calling them directly,
 *    skipping itself to avoid infinite recursion
 *
 * <p>If watermark is disabled or the file is not an image, returns Mono.empty()
 * so the chain proceeds to the next handler (standard behavior).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WatermarkAttachmentHandler implements AttachmentHandler {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
        "jpg", "jpeg", "png", "bmp", "tiff", "tif", "webp", "gif"
    );

    private final ReactiveSettingFetcher settingFetcher;
    private final ExtensionGetter extensionGetter;

    @Override
    public Mono<Attachment> upload(UploadContext context) {
        return settingFetcher.fetch("basic", WatermarkProperties.class)
            .defaultIfEmpty(new WatermarkProperties())
            .flatMap(props -> {
                if (!props.isEnabled()) {
                    log.debug("Watermark disabled, skipping");
                    return Mono.empty();
                }
                if (!isImage(context.file().filename())) {
                    log.debug("File {} is not an image, skipping", context.file().filename());
                    return Mono.empty();
                }
                return processAndDelegate(context, props);
            });
    }

    /**
     * Buffer → watermark → encode → delegate to storage handler.
     *
     * <p>IMPORTANT: Once we subscribe to context.file().content(), the original
     * FilePart content is consumed and cannot be re-read by subsequent handlers.
     * Therefore, if processing fails for any reason, we must still delegate the
     * original bytes (unchanged) to storage handlers — never return Mono.empty().
     */
    private Mono<Attachment> processAndDelegate(UploadContext context, WatermarkProperties props) {
        // Buffer the entire file content (images are typically <50MB, safe to hold in memory)
        return DataBufferUtils.join(context.file().content())
            .flatMap(dataBuffer -> {
                byte[] originalBytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(originalBytes);
                DataBufferUtils.release(dataBuffer);

                // Heavy CPU work: run on bounded-elastic scheduler
                return Mono.fromCallable(() -> {
                    log.info("Processing watermark for: {}", context.file().filename());

                    var image = ImageProcessor.readImage(originalBytes);
                    if (image == null) {
                        log.warn("Failed to decode image: {}, delegating original bytes",
                            context.file().filename());
                        return null;
                    }

                    // Apply watermark
                    image = ImageProcessor.applyWatermark(image, props);

                    // Encode to WebP (or JPEG fallback)
                    return ImageProcessor.encode(image, props);
                }).subscribeOn(Schedulers.boundedElastic())
                    .flatMap(result -> {
                        // Success: build new FilePart with processed image
                        String originalName = context.file().filename();
                        String newName = replaceExtension(originalName, result.extension());
                        var buffer = DefaultDataBufferFactory.sharedInstance.wrap(result.data());
                        var mediaType = MediaType.parseMediaType(result.mediaType());
                        var newFile = new SimpleFilePart(newName, Flux.just(buffer), mediaType);

                        var newContext = UploadOption.builder()
                            .file(newFile)
                            .policy(context.policy())
                            .configMap(context.configMap())
                            .group(context.group())
                            .build();

                        log.info("Watermark applied: {} → {} ({} bytes)",
                            originalName, newName, result.data().length);

                        return delegateToStorageHandlers(newContext);
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        // Image decode failed: delegate original bytes unchanged
                        return delegateOriginalBytes(context, originalBytes);
                    }))
                    .onErrorResume(ex -> {
                        // Any processing error: delegate original bytes as fallback
                        log.error("Watermark processing error, delegating original: {}",
                            ex.getMessage());
                        return delegateOriginalBytes(context, originalBytes);
                    });
            });
    }

    /**
     * Find and call the appropriate storage handler (Local, S3, etc.)
     * by iterating all AttachmentHandler extensions except this one.
     */
    private Mono<Attachment> delegateToStorageHandlers(UploadContext newContext) {
        return extensionGetter.getExtensions(AttachmentHandler.class)
            .filter(handler -> !(handler instanceof WatermarkAttachmentHandler))
            .concatMap(handler -> handler.upload(newContext))
            .next()
            .switchIfEmpty(Mono.defer(() -> {
                log.error("No storage handler found to upload the processed image");
                return Mono.error(new RuntimeException(
                    "No suitable storage handler found for the processed image. "
                        + "Ensure a storage policy (Local or S3) is configured."));
            }));
    }

    /**
     * Fallback: delegate original bytes (unchanged) to storage handlers.
     * Used when watermark processing fails but we've already consumed the content.
     */
    private Mono<Attachment> delegateOriginalBytes(UploadContext context, byte[] originalBytes) {
        var originalMediaType = context.file().headers().getContentType();
        if (originalMediaType == null) {
            originalMediaType = MediaTypeFactory.getMediaType(context.file().filename())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        }
        var buffer = DefaultDataBufferFactory.sharedInstance.wrap(originalBytes);
        var fallbackFile = new SimpleFilePart(
            context.file().filename(), Flux.just(buffer), originalMediaType);
        var fallbackContext = UploadOption.builder()
            .file(fallbackFile)
            .policy(context.policy())
            .configMap(context.configMap())
            .group(context.group())
            .build();
        return delegateToStorageHandlers(fallbackContext);
    }

    /**
     * Check if the file is an image based on extension and/or content-type header.
     */
    private boolean isImage(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String ext = getExtension(filename).toLowerCase();
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return true;
        }
        // Also check via MediaTypeFactory
        return MediaTypeFactory.getMediaType(filename)
            .map(mt -> mt.getType().equalsIgnoreCase("image"))
            .orElse(false);
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    private static String replaceExtension(String filename, String newExt) {
        int dot = filename.lastIndexOf('.');
        String baseName = dot >= 0 ? filename.substring(0, dot) : filename;
        return baseName + "." + newExt;
    }

    // ---- Not handled by this plugin (pass-through) ----

    @Override
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        // Deletion is handled by the actual storage handler (Local/S3)
        return Mono.empty();
    }
}
