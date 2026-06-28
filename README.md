# Halo Plugin: Image Watermark & WebP

**English** | [中文](README.zh-CN.md) | [Bahasa Indonesia](README.id.md)

A plugin for [Halo CMS](https://github.com/halo-dev/halo) that automatically adds text watermarks and converts images to WebP format during attachment uploads.

## Features

- **Automatic text watermark** on all uploaded images (JPG, PNG, BMP, TIFF, WebP, GIF)
- **WebP conversion** for smaller file sizes, with automatic original file fallback if WebP encoding fails
- **Adaptive font sizing** via percentage of image diagonal (consistent across all resolutions) or fixed pixel value
- **9-position anchor** system for watermark placement (corners, edges, center)
- **Full customization** — opacity, margin, compression quality, font family/style/color, text shadow
- **Live preview** — real-time preview on the settings page before saving
- **Storage agnostic** — works with Local storage, S3, Cloudflare R2, or any other storage plugin
- **Tools submenu** — configuration page available directly under Halo's Tools menu

## How It Works

The plugin registers an `AttachmentHandler` with the highest priority (`@Order(HIGHEST_PRECEDENCE)`). When an image is uploaded:

1. The handler intercepts the upload and writes it to a temp file
2. Reads the image bytes into a `BufferedImage`
3. Applies the text watermark using Java2D (`Graphics2D`)
4. Encodes to WebP (via [sejda webp-imageio](https://github.com/sejda-pdf/webp-imageio)) or original file (fallback)
5. Delegates to the original storage handler (Local/S3) with the new processed `FilePart`

Non-image files (PDFs, videos, etc.) are passed through untouched.

## Installation

### From GitHub Releases (recommended)

1. Go to [Releases](https://github.com/manssizz/halo-plugin-watermark/releases)
2. Download the `.jar` file from the latest release
3. In Halo Console → **System → Plugins → Install** → upload the JAR file
4. Enable the plugin
5. Go to **Tools → Image Watermark** to configure

### Manual Build

```bash
git clone https://github.com/manssizz/halo-plugin-watermark.git
cd halo-plugin-watermark
./gradlew build
```

The built JAR is located at `build/libs/halo-plugin-watermark-*.jar`.

### Build via GitHub Actions (no local dependencies needed)

1. Fork or push this repo to GitHub
2. Create a new Release (or push a tag like `v1.0.0`)
3. GitHub Actions will automatically build and attach the JAR to the release

## Configuration

After enabling the plugin, go to **Tools → Image Watermark** in Halo Console:

| Setting | Default | Description |
|---|---|---|
| Enable Watermark | ✅ | Global on/off toggle |
| Convert to WebP | ✅ | Encode output as WebP (falls back to original file) |
| Watermark Text | `www.isiotak.com` | Text displayed on the image |
| Font Size Mode | Percentage | `PERCENTAGE` = % of image diagonal, `PIXEL` = fixed px |
| Font Size | 4 | 4% of diagonal (≈ 24px on 800px wide, ≈ 88px on 4K) |
| Position | Bottom Right | 9 positions available |
| Opacity | 60% | Watermark transparency |
| Margin | 2% | Distance from image edge |
| Output Quality | 80 | WebP/JPEG compression quality |
| Font Family | SansSerif | Any font available on the server |
| Font Style | Bold | Normal / Bold / Italic / Bold Italic |
| Font Color | #FFFFFF | Watermark text color (hex) |
| Shadow | ✅ | Drop shadow behind text for readability |

## Comparison with the Original `webp.sh` Script

| Aspect | `webp.sh` Script | This Plugin |
|---|---|---|
| Font sizing | `width / 80` (fixed ratio) — too large on small images, too small on large ones | **Diagonal percentage** — proportional at all resolutions, clamped min 10px / max 500px |
| Margin | Fixed `+20+20` px | **Diagonal percentage** — proportional |
| Dependencies | ImageMagick + cwebp (must be installed on server) | **Pure Java** — no external installs needed (native WebP lib bundled in JAR) |
| Integration | Manual, requires running a separate script | **Automatic** — integrated directly into Halo's upload flow |
| Configuration | Edit the script directly | **Visual UI** with live preview |

## Technical Notes

- **WebP encoding** uses `org.sejda.imageio:webp-imageio` which bundles native libraries for Linux 64-bit, Windows 64-bit, and macOS 64-bit. If the native lib fails to load (e.g. on ARM), the plugin automatically falls back to JPEG.
- The plugin registers WebP ImageIO SPI manually at startup to work around PF4J classloader isolation (Java's `ImageIO` ServiceLoader doesn't see plugin-scoped JARs).
- Image data is consumed via `transferTo(tempFile)` instead of in-memory buffering, which ensures Spring's multipart parser fully reads the HTTP body boundaries before processing begins.
- Images are processed **in memory** after reading from the temp file — for very large images (>100MB), ensure the Halo JVM has sufficient heap space.
- The plugin **does not modify** existing images in storage — it only processes new uploads.
- Compiled directly against `run.halo.app:api:2.22.5` (not the `run.halo.tools.platform:plugin` BOM) for broad compatibility with Halo 2.20–2.22.x.
- Uses custom GitHub Actions workflows instead of `halo-sigs/reusable-workflows` to avoid pnpm store caching issues.

## Compatibility

- Halo 2.20.x – 2.22.x (compiled against api 2.22.5; tested on 2.22.0)
- Java 21+
- S3 Plugin (optional, for S3 storage)
- For Halo 2.24+ the `UploadContext` API changed (`group()` was added) — a separate branch/version would be needed

## License

[GPL-3.0](LICENSE)
