# Halo 插件：图片水印 & WebP 转换
<p align="center">
[English](README.md) | **中文** | [Bahasa Indonesia](README.id.md)
</p>

一款用于 [Halo CMS](https://github.com/halo-dev/halo) 的插件，在上传附件时自动为图片添加文字水印并转换为 WebP 格式。

## 功能特性

- **自动文字水印** — 支持所有上传的图片格式（JPG、PNG、BMP、TIFF、WebP、GIF）
- **WebP 转换** — 自动转换为 WebP 以减小文件体积，如 WebP 编码失败则自动回退为 JPEG/PNG
- **自适应字体大小** — 可按图片对角线百分比（所有分辨率下比例一致）或固定像素值设置
- **9 个水印位置** — 四角、四边中点、居中
- **全面自定义** — 透明度、边距、压缩质量、字体系列/样式/颜色、文字阴影
- **实时预览** — 保存前可在设置页面实时预览效果
- **存储无关** — 兼容本地存储、S3、Cloudflare R2 或其他存储插件
- **工具菜单** — 配置页面直接显示在 Halo 的工具菜单中

## 工作原理

插件注册了一个具有最高优先级（`@Order(HIGHEST_PRECEDENCE)`）的 `AttachmentHandler`。当图片上传时：

1. 处理器拦截上传并写入临时文件
2. 将图片字节读取为 `BufferedImage`
3. 使用 Java2D（`Graphics2D`）添加文字水印
4. 编码为 WebP（通过 [sejda webp-imageio](https://github.com/sejda-pdf/webp-imageio)）或 JPEG/PNG（回退）
5. 将处理后的 `FilePart` 委托给原始存储处理器（本地/S3）

非图片文件（PDF、视频等）将直接透传，不做处理。

## 安装

### 从 GitHub Release 安装（推荐）

1. 前往 [Releases](https://github.com/manssizz/halo-plugin-watermark/releases)
2. 下载最新版本的 `.jar` 文件
3. 在 Halo 控制台 → **系统 → 插件 → 安装** → 上传 JAR 文件
4. 启用插件
5. 前往 **工具 → Image Watermark** 进行配置

### 手动构建

```bash
git clone https://github.com/manssizz/halo-plugin-watermark.git
cd halo-plugin-watermark
./gradlew build
```

构建后的 JAR 文件位于 `build/libs/halo-plugin-watermark-*.jar`。

### 通过 GitHub Actions 构建（无需本地安装依赖）

1. Fork 或推送此仓库到 GitHub
2. 创建一个新的 Release（或推送标签如 `v1.0.0`）
3. GitHub Actions 将自动构建并将 JAR 附加到 Release

## 配置

启用插件后，在 Halo 控制台中前往 **工具 → Image Watermark**：

| 设置项 | 默认值 | 说明 |
|---|---|---|
| 启用水印 | ✅ | 全局开关 |
| 转换为 WebP | ✅ | 输出编码为 WebP（失败时回退为 JPEG/PNG） |
| 水印文字 | `halo.dev` | 显示在图片上的水印文本（留空则不启用水印） |
| 字体大小模式 | 百分比 | `PERCENTAGE` = 图片对角线的百分比，`PIXEL` = 固定像素值 |
| 字体大小 | 2 | 对角线的 2%（800px 宽 ≈ 20px，4K ≈ 88px） |
| 位置 | 右下角 | 可选 9 个位置 |
| 透明度 | 60% | 水印透明度 |
| 边距 | 2% | 距图片边缘的距离 |
| 输出质量 | 80 | WebP/JPEG 压缩质量 |
| 字体系列 | SansSerif | 服务器上可用的任意字体 |
| 字体样式 | Bold | Normal / Bold / Italic / Bold Italic |
| 字体颜色 | #FFFFFF | 水印文字颜色（十六进制） |
| 阴影 | ✅ | 文字后方的投影，提高可读性 |

## 与原始 `webp.sh` 脚本的对比

| 方面 | `webp.sh` 脚本 | 本插件 |
|---|---|---|
| 字体大小 | `width / 80`（固定比例）— 小图过大、大图过小 | **对角线百分比** — 所有分辨率下比例一致，限制最小 10px / 最大 500px |
| 边距 | 固定 `+20+20` px | **对角线百分比** — 自适应 |
| 依赖 | ImageMagick + cwebp（需在服务器上安装） | **纯 Java** — 无需额外安装（WebP 原生库打包在 JAR 中） |
| 集成 | 手动运行独立脚本 | **自动** — 直接集成到 Halo 的上传流程 |
| 配置 | 直接编辑脚本 | **可视化界面** 带实时预览 |

## 技术说明

- **WebP 编码** 使用 `org.sejda.imageio:webp-imageio`，该库打包了 Linux 64 位、Windows 64 位和 macOS 64 位的原生库。如原生库加载失败（如 ARM 架构），插件将自动回退为 JPEG。
- 插件在启动时手动注册 WebP ImageIO SPI，以解决 PF4J 类加载器隔离问题（Java 的 `ImageIO` ServiceLoader 无法发现插件作用域的 JAR）。
- 图片数据通过 `transferTo(tempFile)` 消费，而非内存缓冲，确保 Spring 的 multipart 解析器在处理开始前完整读取 HTTP body 边界。
- 读取临时文件后图片在**内存中**处理 — 对于超大图片（>100MB），请确保 Halo JVM 有足够的堆空间。
- 插件**不会修改**存储中已有的图片 — 仅处理新上传的图片。
- 直接编译自 `run.halo.app:api:2.22.5`（未使用 `run.halo.tools.platform:plugin` BOM），以广泛兼容 Halo 2.20–2.22.x。
- 使用自定义 GitHub Actions 工作流，而非 `halo-sigs/reusable-workflows`，以避免 pnpm store 缓存问题。

## 兼容性

- Halo 2.20.x – 2.22.x（编译自 api 2.22.5；已在 2.22.0 上测试）
- Java 21+
- S3 插件（可选，用于 S3 存储）
- Halo 2.24+ 的 `UploadContext` API 发生了变化（新增了 `group()`），需要单独的分支/版本支持

## 许可证

[GPL-3.0](LICENSE)
