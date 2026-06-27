# Halo Plugin: Image Watermark & WebP

Plugin untuk [Halo CMS](https://github.com/halo-dev/halo) yang otomatis menambahkan watermark teks dan mengkonversi gambar ke format WebP saat upload attachment.

## Fitur

- **Watermark teks otomatis** — diterapkan ke semua gambar yang diupload (JPG, PNG, BMP, TIFF, WebP, GIF)
- **Konversi WebP** — gambar otomatis dikonversi ke WebP untuk ukuran file lebih kecil (dengan fallback JPEG jika WebP gagal)
- **Adaptive font sizing** — ukuran font bisa diatur via persentase diagonal gambar (proporsional di semua resolusi) atau piksel tetap
- **9 posisi watermark** — sudut, tepi tengah, dan tengah gambar
- **Kustomisasi lengkap** — opacity, margin, kualitas kompresi, font family/style/color, shadow
- **Live preview** — preview real-time di halaman settings sebelum menyimpan
- **Storage agnostic** — bekerja dengan Local storage maupun S3/Cloudflare R2 (atau storage plugin lainnya)
- **Submenu Tools** — halaman konfigurasi langsung tersedia di menu Tools Halo

## Cara Kerja

Plugin ini mendaftarkan `AttachmentHandler` dengan prioritas tertinggi (`@Order(HIGHEST_PRECEDENCE)`).
Saat gambar diupload:

1. Handler meng-intercept upload
2. Membaca byte gambar → `BufferedImage`
3. Menerapkan watermark teks via Java2D (`Graphics2D`)
4. Meng-encode ke WebP (via [sejda webp-imageio](https://github.com/sejda-pdf/webp-imageio)) atau JPEG (fallback)
5. Mendelegasikan ke storage handler asli (Local/S3) dengan `FilePart` baru

File non-gambar (PDF, video, dll) tidak diproses dan langsung diteruskan ke storage handler.

## Instalasi

### Dari GitHub Release (rekomendasi)

1. Buka [Releases](https://github.com/nicx-next/halo-plugin-watermark/releases)
2. Download file `.jar` dari release terbaru
3. Di Halo Console → **System → Plugins → Install** → upload file JAR
4. Aktifkan plugin
5. Buka **Tools → Image Watermark** untuk konfigurasi

### Build Manual

```bash
git clone https://github.com/nicx-next/halo-plugin-watermark.git
cd halo-plugin-watermark
./gradlew build
```

File JAR hasil build ada di `build/libs/halo-plugin-watermark-*.jar`.

### Build via GitHub Actions (tanpa install dependency di laptop)

1. Fork/push repo ini ke GitHub
2. Buat Release baru di GitHub (atau push tag `v1.0.0`)
3. GitHub Actions akan otomatis build dan attach file JAR ke release

## Pengaturan

Setelah plugin aktif, buka **Tools → Image Watermark** di Halo Console:

| Setting | Default | Keterangan |
|---|---|---|
| Aktifkan Watermark | ✅ | On/off global |
| Konversi ke WebP | ✅ | Encode ke WebP (fallback JPEG) |
| Teks Watermark | `www.isiotak.com` | Teks yang ditampilkan |
| Mode Ukuran Font | Persentase | `PERCENTAGE` = % diagonal gambar, `PIXEL` = px tetap |
| Ukuran Font | 4 | 4% diagonal (≈ 24px di 800px, ≈ 88px di 4K) |
| Posisi | Kanan Bawah | 9 posisi tersedia |
| Opacity | 60% | Transparansi watermark |
| Margin | 2% | Jarak dari tepi |
| Kualitas Output | 80 | Kualitas kompresi WebP/JPEG |
| Font Family | SansSerif | Font yang tersedia di server |
| Font Style | Bold | Normal/Bold/Italic/Bold Italic |
| Warna Font | #FFFFFF | Warna teks watermark |
| Shadow | ✅ | Bayangan di belakang teks |

## Perbedaan dengan Script `webp.sh` Asli

| Aspek | Script `webp.sh` | Plugin ini |
|---|---|---|
| Font sizing | `width / 80` (fixed ratio) — terlalu besar di gambar kecil, terlalu kecil di gambar besar | **Persentase diagonal** — proporsional di semua resolusi, dengan clamp min 10px / max 500px |
| Margin | Fixed `+20+20` px | **Persentase diagonal** — proporsional |
| Dependency | ImageMagick + cwebp (harus diinstall di server) | **Pure Java** — tidak perlu install apapun (native WebP lib dibundel dalam JAR) |
| Integrasi | Manual, harus jalankan script terpisah | **Otomatis** — terintegrasi langsung ke alur upload Halo |
| Konfigurasi | Edit script langsung | **UI visual** dengan live preview |

## Catatan Teknis

- **WebP encoding** menggunakan `org.sejda.imageio:webp-imageio` yang membundel native library untuk Linux 64-bit, Windows 64-bit, dan macOS 64-bit. Jika native lib gagal dimuat (misal di ARM), plugin akan otomatis fallback ke JPEG.
- Plugin memproses gambar **di memory** — untuk gambar sangat besar (>100MB), pastikan JVM Halo punya heap cukup.
- Plugin ini **tidak mengubah** gambar yang sudah ada di storage — hanya memproses gambar baru saat upload.

## Kompatibilitas

- Halo >= 2.20.0
- Java 21+
- Plugin S3 (opsional, untuk S3 storage)

## Lisensi

[GPL-3.0](LICENSE)
