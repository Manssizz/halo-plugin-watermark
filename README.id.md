# Halo Plugin: Image Watermark & WebP

[English](README.md) | [中文](README.zh-CN.md) | **Bahasa Indonesia**

Plugin untuk [Halo CMS](https://github.com/halo-dev/halo) yang secara otomatis menambahkan watermark teks dan mengkonversi gambar ke format WebP saat upload attachment.

## Fitur

- **Watermark teks otomatis** pada semua gambar yang diupload (JPG, PNG, BMP, TIFF, WebP, GIF)
- **Konversi WebP** untuk ukuran file lebih kecil, dengan fallback otomatis ke file asli jika encoding WebP gagal
- **Ukuran font adaptif** via persentase diagonal gambar (proporsional di semua resolusi) atau nilai piksel tetap
- **9 posisi watermark** — sudut, tepi tengah, dan tengah gambar
- **Kustomisasi lengkap** — opacity, margin, kualitas kompresi, font family/style/color, shadow teks
- **Live preview** — preview real-time di halaman settings sebelum menyimpan
- **Storage agnostic** — bekerja dengan Local storage, S3, Cloudflare R2, atau storage plugin lainnya
- **Submenu Tools** — halaman konfigurasi tersedia langsung di menu Tools Halo

## Cara Kerja

Plugin ini mendaftarkan `AttachmentHandler` dengan prioritas tertinggi (`@Order(HIGHEST_PRECEDENCE)`). Saat gambar diupload:

1. Handler meng-intercept upload dan menulis ke file sementara
2. Membaca byte gambar ke `BufferedImage`
3. Menerapkan watermark teks via Java2D (`Graphics2D`)
4. Meng-encode ke WebP (via [sejda webp-imageio](https://github.com/sejda-pdf/webp-imageio)) atau file asli (fallback)
5. Mendelegasikan ke storage handler asli (Local/S3) dengan `FilePart` baru

File non-gambar (PDF, video, dll) langsung diteruskan tanpa diproses.

## Instalasi

### Dari GitHub Release (rekomendasi)

1. Buka [Releases](https://github.com/manssizz/halo-plugin-watermark/releases)
2. Download file `.jar` dari release terbaru
3. Di Halo Console → **System → Plugins → Install** → upload file JAR
4. Aktifkan plugin
5. Buka **Tools → Image Watermark** untuk konfigurasi

### Build Manual

```bash
git clone https://github.com/manssizz/halo-plugin-watermark.git
cd halo-plugin-watermark
./gradlew build
```

File JAR hasil build ada di `build/libs/halo-plugin-watermark-*.jar`.

### Build via GitHub Actions (tanpa install dependency di laptop)

1. Fork atau push repo ini ke GitHub
2. Buat Release baru (atau push tag seperti `v1.0.0`)
3. GitHub Actions akan otomatis build dan attach file JAR ke release

## Pengaturan

Setelah plugin aktif, buka **Tools → Image Watermark** di Halo Console:

| Setting | Default | Keterangan |
|---|---|---|
| Aktifkan Watermark | ✅ | On/off global |
| Konversi ke WebP | ✅ | Encode output ke WebP (fallback file asli) |
| Teks Watermark | `www.isiotak.com` | Teks yang ditampilkan pada gambar |
| Mode Ukuran Font | Persentase | `PERCENTAGE` = % diagonal gambar, `PIXEL` = px tetap |
| Ukuran Font | 4 | 4% diagonal (≈ 24px di 800px, ≈ 88px di 4K) |
| Posisi | Kanan Bawah | 9 posisi tersedia |
| Opacity | 60% | Transparansi watermark |
| Margin | 2% | Jarak dari tepi gambar |
| Kualitas Output | 80 | Kualitas kompresi WebP/JPEG |
| Font Family | SansSerif | Font yang tersedia di server |
| Font Style | Bold | Normal / Bold / Italic / Bold Italic |
| Warna Font | #FFFFFF | Warna teks watermark (hex) |
| Shadow | ✅ | Bayangan di belakang teks untuk keterbacaan |

## Perbandingan dengan Script `webp.sh` Asli

| Aspek | Script `webp.sh` | Plugin ini |
|---|---|---|
| Ukuran font | `width / 80` (rasio tetap) — terlalu besar di gambar kecil, terlalu kecil di gambar besar | **Persentase diagonal** — proporsional di semua resolusi, clamp min 10px / max 500px |
| Margin | Fixed `+20+20` px | **Persentase diagonal** — proporsional |
| Dependency | ImageMagick + cwebp (harus diinstall di server) | **Pure Java** — tidak perlu install apapun (native WebP lib dibundel dalam JAR) |
| Integrasi | Manual, jalankan script terpisah | **Otomatis** — terintegrasi langsung ke alur upload Halo |
| Konfigurasi | Edit script langsung | **UI visual** dengan live preview |

## Catatan Teknis

- **WebP encoding** menggunakan `org.sejda.imageio:webp-imageio` yang membundel native library untuk Linux 64-bit, Windows 64-bit, dan macOS 64-bit. Jika native lib gagal dimuat (misal di ARM), plugin otomatis fallback ke JPEG.
- Plugin mendaftarkan WebP ImageIO SPI secara manual saat startup untuk mengatasi isolasi classloader PF4J (Java `ImageIO` ServiceLoader tidak bisa menemukan JAR dalam scope plugin).
- Data gambar di-consume via `transferTo(tempFile)` bukan buffer di memory, memastikan multipart parser Spring selesai membaca boundary HTTP body sebelum proses dimulai.
- Setelah dibaca dari temp file, gambar diproses **di memory** — untuk gambar sangat besar (>100MB), pastikan JVM Halo punya heap cukup.
- Plugin **tidak mengubah** gambar yang sudah ada di storage — hanya memproses upload baru.
- Di-compile langsung dari `run.halo.app:api:2.22.5` (bukan `run.halo.tools.platform:plugin` BOM) untuk kompatibilitas luas dengan Halo 2.20–2.22.x.
- Menggunakan custom GitHub Actions workflow, bukan `halo-sigs/reusable-workflows`, untuk menghindari masalah cache pnpm store.

## Kompatibilitas

- Halo 2.20.x – 2.22.x (compile dari api 2.22.5; ditest di 2.22.0)
- Java 21+
- Plugin S3 (opsional, untuk S3 storage)
- Untuk Halo 2.24+ API `UploadContext` berubah (ada tambahan `group()`) — perlu branch/versi terpisah

## Lisensi

[GPL-3.0](LICENSE)
