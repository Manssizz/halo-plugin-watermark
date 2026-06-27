package run.halo.watermark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
 * <p>Uses transferTo(tempFile) to consume the multipart body safely —
 * this lets Spring's multipart parser fully read the boundary markers
 * before we process the image bytes, avoiding DecodingException.
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
     * transferTo(tempFile) → read bytes → watermark → encode → delegate.
     *
     * <p>Key: transferTo() lets the multipart parser fully consume the body part
     * (including boundary markers) before we touch the bytes. This prevents the
     * "Could not find end of body" DecodingException from Netty's multipart parser.
     */
    private Mono<Attachment> processAndDelegate(UploadContext context, WatermarkProperties props) {
        return Mono.defer(() -> {
            final Path tempFile;
            try {
                tempFile = Files.createTempFile("halo-watermark-", ".tmp");
            } catch (IOException e) {
                return Mono.error(e);
            }

            // Step 1: transferTo drains the multipart body fully (boundary included)
            return context.file().transferTo(tempFile)
                // Step 2: process on bounded-elastic (CPU-heavy)
                .then(Mono.fromCallable(() -> {
                    try {
                        log.info("Processing watermark for: {}", context.file().filename());
                        byte[] originalBytes = Files.readAllBytes(tempFile);

                        var image = ImageProcessor.readImage(originalBytes);
                        if (image == null) {
                            log.warn("Failed to decode image: {}, delegating original",
                                context.file().filename());
                            // Return original bytes unchanged
                            return new ProcessedFile(
                                originalBytes,
                                context.file().filename(),
                                getContentType(context),
                                false
                            );
                        }

                        // Apply watermark
                        image = ImageProcessor.applyWatermark(image, props);

                        // Encode to WebP (or JPEG fallback)
                        var encoded = ImageProcessor.encode(image, props);
                        var newName = replaceExtension(
                            context.file().filename(), encoded.extension());

                        return new ProcessedFile(
                            encoded.data(),
                            newName,
                            encoded.mediaType(),
                            true
                        );
                    } finally {
                        Files.deleteIfExists(tempFile);
                    }
                }).subscribeOn(Schedulers.boundedElastic()))
                // Step 3: delegate to storage handler (Local / S3)
                .flatMap(result -> {
                    var buffer = DefaultDataBufferFactory.sharedInstance
                        .wrap(result.data());
                    var mediaType = MediaType.parseMediaType(result.mediaType());
                    var newFile = new SimpleFilePart(
                        result.filename(), Flux.just(buffer), mediaType);

                    var newContext = new UploadOption(
                        newFile, context.policy(),
                        context.configMap(), context.group());

                    if (result.processed()) {
                        log.info("Watermark applied: {} -> {} ({} bytes)",
                            context.file().filename(), result.filename(),
                            result.data().length);
                    }

                    return delegateToStorageHandlers(newContext);
                })
                // Safety net: if anything fails, clean up temp file
                .doOnError(ex -> {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {
                    }
                    log.error("Watermark handler error: {}", ex.getMessage());
                });
        });
    }

    /**
     * Result of processing: bytes + metadata for the new FilePart.
     */
    private record ProcessedFile(
        byte[] data, String filename, String mediaType, boolean processed) {
    }

    /**
     * Delegate to actual storage handlers (Local, S3, etc.), skipping self.
     */
    private Mono<Attachment> delegateToStorageHandlers(UploadContext newContext) {
        return extensionGetter.getExtensions(AttachmentHandler.class)
            .filter(handler -> !(handler instanceof WatermarkAttachmentHandler))
            .concatMap(handler -> handler.upload(newContext))
            .next()
            .switchIfEmpty(Mono.defer(() -> {
                log.error("No storage handler found for the processed image");
                return Mono.error(new RuntimeException(
                    "No suitable storage handler found. "
                        + "Ensure a storage policy (Local or S3) is configured."));
            }));
    }

    private String getContentType(UploadContext context) {
        var ct = context.file().headers().getContentType();
        if (ct != null) {
            return ct.toString();
        }
        return MediaTypeFactory.getMediaType(context.file().filename())
            .map(MediaType::toString)
            .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    private boolean isImage(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String ext = getExtension(filename).toLowerCase();
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return true;
        }
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

    @Override
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        return Mono.empty();
    }
}
