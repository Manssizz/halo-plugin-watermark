<script setup lang="ts">
import { axiosInstance } from "@halo-dev/api-client";
import {
  VButton,
  VCard,
  VPageHeader,
  VSpace,
  VLoading,
  Toast,
} from "@halo-dev/components";
import { onMounted, reactive, ref, watch, nextTick } from "vue";
import RiImageEditLine from "~icons/ri/image-edit-line";

const CONFIGMAP_NAME = "watermark-configMap";
const GROUP_NAME = "basic";

interface WatermarkConfig {
  enabled: boolean;
  enableWebpConversion: boolean;
  watermarkText: string;
  fontSizeMode: string;
  fontSize: number;
  position: string;
  opacity: number;
  margin: number;
  quality: number;
  fontFamily: string;
  fontStyle: string;
  fontColor: string;
  enableShadow: boolean;
}

const defaultConfig: WatermarkConfig = {
  enabled: true,
  enableWebpConversion: true,
  watermarkText: "www.isiotak.com",
  fontSizeMode: "PERCENTAGE",
  fontSize: 4,
  position: "BOTTOM_RIGHT",
  opacity: 60,
  margin: 2,
  quality: 80,
  fontFamily: "SansSerif",
  fontStyle: "BOLD",
  fontColor: "#FFFFFF",
  enableShadow: true,
};

const positionOptions = [
  { label: "Top Left", value: "TOP_LEFT" },
  { label: "Top Center", value: "TOP_CENTER" },
  { label: "Top Right", value: "TOP_RIGHT" },
  { label: "Center Left", value: "CENTER_LEFT" },
  { label: "Center", value: "CENTER" },
  { label: "Center Right", value: "CENTER_RIGHT" },
  { label: "Bottom Left", value: "BOTTOM_LEFT" },
  { label: "Bottom Center", value: "BOTTOM_CENTER" },
  { label: "Bottom Right", value: "BOTTOM_RIGHT" },
];

const fontStyleOptions = [
  { label: "Normal", value: "PLAIN" },
  { label: "Bold", value: "BOLD" },
  { label: "Italic", value: "ITALIC" },
  { label: "Bold Italic", value: "BOLD_ITALIC" },
];

const config = reactive<WatermarkConfig>({ ...defaultConfig });
const configMapVersion = ref("");
const isLoading = ref(true);
const isSaving = ref(false);
const canvasRef = ref<HTMLCanvasElement | null>(null);

// ===== Load settings from ConfigMap =====
const loadSettings = async () => {
  isLoading.value = true;
  try {
    const { data } = await axiosInstance.get(
      `/api/v1alpha1/configmaps/${CONFIGMAP_NAME}`
    );
    configMapVersion.value = data.metadata?.version || "";
    const raw = data.data?.[GROUP_NAME];
    if (raw) {
      const parsed = JSON.parse(raw);
      Object.assign(config, { ...defaultConfig, ...parsed });
    }
  } catch (e: unknown) {
    const err = e as { response?: { status?: number } };
    if (err?.response?.status === 404) {
      Object.assign(config, { ...defaultConfig });
    } else {
      console.error("Failed to load settings", e);
      Toast.error("Failed to load watermark settings");
    }
  }
  isLoading.value = false;
};

// ===== Save settings to ConfigMap =====
const saveSettings = async () => {
  isSaving.value = true;
  try {
    const payload = {
      data: {
        [GROUP_NAME]: JSON.stringify(config),
      },
      apiVersion: "v1alpha1",
      kind: "ConfigMap",
      metadata: {
        name: CONFIGMAP_NAME,
        version: configMapVersion.value || undefined,
      },
    };
    const { data } = await axiosInstance.put(
      `/api/v1alpha1/configmaps/${CONFIGMAP_NAME}`,
      payload
    );
    configMapVersion.value = data.metadata?.version || "";
    Toast.success("Watermark settings saved successfully");
  } catch (e) {
    console.error("Failed to save settings", e);
    Toast.error("Failed to save watermark settings");
  }
  isSaving.value = false;
};

const resetSettings = () => {
  Object.assign(config, { ...defaultConfig });
  drawPreview();
};

// ===== Canvas Live Preview =====
const drawPreview = () => {
  const canvas = canvasRef.value;
  if (!canvas) return;

  const ctx = canvas.getContext("2d");
  if (!ctx) return;

  const W = canvas.width;
  const H = canvas.height;

  // Draw sample background (gradient simulating a photo)
  const grad = ctx.createLinearGradient(0, 0, W, H);
  grad.addColorStop(0, "#4a90d9");
  grad.addColorStop(0.5, "#357abd");
  grad.addColorStop(1, "#2c6fad");
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, W, H);

  // Draw some "scenery" shapes to simulate a photo
  ctx.fillStyle = "#3d8c40";
  ctx.fillRect(0, H * 0.65, W, H * 0.35);
  ctx.fillStyle = "#f5d442";
  ctx.beginPath();
  ctx.arc(W * 0.8, H * 0.2, 30, 0, Math.PI * 2);
  ctx.fill();

  if (!config.watermarkText || !config.enabled) return;

  // Calculate font size (same logic as Java backend)
  const diagonal = Math.sqrt(W * W + H * H);
  let fontSize: number;
  if (config.fontSizeMode === "PERCENTAGE") {
    fontSize = Math.round((diagonal * config.fontSize) / 100);
  } else {
    // Scale pixel mode relative to preview size vs typical 1920px image
    fontSize = Math.round((config.fontSize * W) / 1920);
  }
  fontSize = Math.max(8, Math.min(200, fontSize));

  // Build font string
  let fontWeight = "";
  let fontStyleCss = "";
  if (config.fontStyle === "BOLD" || config.fontStyle === "BOLD_ITALIC") {
    fontWeight = "bold";
  }
  if (config.fontStyle === "ITALIC" || config.fontStyle === "BOLD_ITALIC") {
    fontStyleCss = "italic";
  }
  const fontStr = `${fontStyleCss} ${fontWeight} ${fontSize}px ${config.fontFamily || "sans-serif"}`.trim();
  ctx.font = fontStr;

  const textMetrics = ctx.measureText(config.watermarkText);
  const textWidth = textMetrics.width;
  const textHeight = fontSize;
  const marginPx = Math.round((diagonal * config.margin) / 100);

  // Calculate position
  let x = 0;
  let y = 0;
  const pos = config.position;

  if (pos.includes("LEFT")) x = marginPx;
  else if (pos.includes("RIGHT")) x = W - textWidth - marginPx;
  else x = (W - textWidth) / 2;

  if (pos.startsWith("TOP")) y = marginPx + textHeight;
  else if (pos.startsWith("BOTTOM")) y = H - marginPx;
  else y = (H + textHeight) / 2;

  const alpha = config.opacity / 100;

  // Shadow
  if (config.enableShadow) {
    const shadowOff = Math.max(1, Math.round(fontSize / 20));
    ctx.globalAlpha = alpha * 0.6;
    ctx.fillStyle = "#000000";
    ctx.fillText(config.watermarkText, x + shadowOff, y + shadowOff);
  }

  // Watermark text
  ctx.globalAlpha = alpha;
  ctx.fillStyle = config.fontColor || "#FFFFFF";
  ctx.fillText(config.watermarkText, x, y);
  ctx.globalAlpha = 1.0;
};

// Watch config changes → redraw preview
watch(config, () => nextTick(drawPreview), { deep: true });

onMounted(async () => {
  await loadSettings();
  await nextTick();
  drawPreview();
});
</script>

<template>
  <VPageHeader title="Image Watermark & WebP">
    <template #icon>
      <RiImageEditLine class="wm-icon" />
    </template>
    <template #actions>
      <VSpace>
        <VButton size="sm" @click="resetSettings">Reset Default</VButton>
        <VButton type="primary" :loading="isSaving" @click="saveSettings">
          Save
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>

  <div class="wm-container">
    <VLoading v-if="isLoading" />

    <template v-else>
      <div class="wm-grid">
        <!-- Left: Settings Form -->
        <VCard class="wm-card">
          <template #header>
            <div class="wm-card-header">Watermark Settings</div>
          </template>

          <div class="wm-form">
            <!-- Toggles -->
            <div class="wm-field">
              <label class="wm-toggle-row">
                <input type="checkbox" v-model="config.enabled" />
                <span>Enable Watermark</span>
              </label>
            </div>
            <div class="wm-field">
              <label class="wm-toggle-row">
                <input type="checkbox" v-model="config.enableWebpConversion" />
                <span>Convert to WebP</span>
              </label>
            </div>

            <!-- Text -->
            <div class="wm-field">
              <label class="wm-label">Watermark Text</label>
              <input
                type="text"
                class="wm-input"
                v-model="config.watermarkText"
                placeholder="www.example.com"
              />
            </div>

            <!-- Font Size Mode -->
            <div class="wm-field">
              <label class="wm-label">Font Size Mode</label>
              <div class="wm-radio-group">
                <label class="wm-radio">
                  <input
                    type="radio"
                    v-model="config.fontSizeMode"
                    value="PERCENTAGE"
                  />
                  <span>Percentage (%)</span>
                </label>
                <label class="wm-radio">
                  <input
                    type="radio"
                    v-model="config.fontSizeMode"
                    value="PIXEL"
                  />
                  <span>Pixels (px)</span>
                </label>
              </div>
            </div>

            <!-- Font Size Value -->
            <div class="wm-field">
              <label class="wm-label">
                {{
                  config.fontSizeMode === "PERCENTAGE"
                    ? "Font Size (% diagonal)"
                    : "Font Size (px)"
                }}
              </label>
              <input
                type="number"
                class="wm-input wm-input-narrow"
                v-model.number="config.fontSize"
                :min="config.fontSizeMode === 'PERCENTAGE' ? 0.5 : 8"
                :max="config.fontSizeMode === 'PERCENTAGE' ? 20 : 500"
                :step="config.fontSizeMode === 'PERCENTAGE' ? 0.5 : 1"
              />
              <span class="wm-hint">
                {{
                  config.fontSizeMode === "PERCENTAGE"
                    ? "Recommended: 3-5%"
                    : "Recommended: 24-72px"
                }}
              </span>
            </div>

            <!-- Position -->
            <div class="wm-field">
              <label class="wm-label">Watermark Position</label>
              <select class="wm-select" v-model="config.position">
                <option
                  v-for="opt in positionOptions"
                  :key="opt.value"
                  :value="opt.value"
                >
                  {{ opt.label }}
                </option>
              </select>
            </div>

            <!-- Opacity -->
            <div class="wm-field">
              <label class="wm-label">Opacity: {{ config.opacity }}%</label>
              <input
                type="range"
                class="wm-range"
                v-model.number="config.opacity"
                min="0"
                max="100"
                step="5"
              />
            </div>

            <!-- Margin -->
            <div class="wm-field">
              <label class="wm-label">Margin: {{ config.margin }}%</label>
              <input
                type="range"
                class="wm-range"
                v-model.number="config.margin"
                min="0"
                max="30"
                step="0.5"
              />
            </div>

            <!-- Quality -->
            <div class="wm-field">
              <label class="wm-label"
                >Output Quality: {{ config.quality }}</label
              >
              <input
                type="range"
                class="wm-range"
                v-model.number="config.quality"
                min="1"
                max="100"
                step="5"
              />
            </div>

            <!-- Font Color -->
            <div class="wm-field">
              <label class="wm-label">Font Color</label>
              <div class="wm-color-row">
                <input
                  type="color"
                  v-model="config.fontColor"
                  class="wm-color-picker"
                />
                <input
                  type="text"
                  class="wm-input wm-input-narrow"
                  v-model="config.fontColor"
                  placeholder="#FFFFFF"
                />
              </div>
            </div>

            <!-- Font Style -->
            <div class="wm-field">
              <label class="wm-label">Font Style</label>
              <select class="wm-select" v-model="config.fontStyle">
                <option
                  v-for="opt in fontStyleOptions"
                  :key="opt.value"
                  :value="opt.value"
                >
                  {{ opt.label }}
                </option>
              </select>
            </div>

            <!-- Font Family -->
            <div class="wm-field">
              <label class="wm-label">Font Family</label>
              <input
                type="text"
                class="wm-input"
                v-model="config.fontFamily"
                placeholder="SansSerif"
              />
            </div>

            <!-- Shadow -->
            <div class="wm-field">
              <label class="wm-toggle-row">
                <input type="checkbox" v-model="config.enableShadow" />
                <span>Enable Shadow</span>
              </label>
            </div>
          </div>
        </VCard>

        <!-- Right: Live Preview -->
        <VCard class="wm-card">
          <template #header>
            <div class="wm-card-header">
              Live Preview
              <span class="wm-preview-hint"
                >(simulated on a 640×400 image)</span
              >
            </div>
          </template>

          <div class="wm-preview-wrap">
            <canvas
              ref="canvasRef"
              width="640"
              height="400"
              class="wm-canvas"
            ></canvas>
          </div>

          <div class="wm-info-box">
            <p>
              <strong>Status:</strong>
              {{ config.enabled ? "✅ Watermark active" : "⛔ Watermark disabled" }}
            </p>
            <p>
              <strong>Output:</strong>
              {{ config.enableWebpConversion ? "WebP" : "Original format (JPEG fallback)" }}
              @ quality {{ config.quality }}
            </p>
            <p>
              <strong>Font:</strong>
              {{ config.fontSize
              }}{{ config.fontSizeMode === "PERCENTAGE" ? "% diagonal" : "px" }}
              {{ config.fontStyle }} {{ config.fontFamily }}
            </p>
          </div>
        </VCard>
      </div>
    </template>
  </div>
</template>

<style scoped>
.wm-icon {
  margin-right: 0.5rem;
  align-self: center;
}

.wm-container {
  padding: 0 1rem 2rem;
}

.wm-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  align-items: start;
}

@media (max-width: 960px) {
  .wm-grid {
    grid-template-columns: 1fr;
  }
}

.wm-card {
  overflow: visible;
}

.wm-card-header {
  font-weight: 600;
  font-size: 0.95rem;
  padding: 0.75rem 1rem;
}

.wm-preview-hint {
  font-weight: 400;
  font-size: 0.8rem;
  color: #999;
  margin-left: 0.5rem;
}

.wm-form {
  padding: 0 1rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.wm-field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.wm-label {
  font-size: 0.85rem;
  font-weight: 500;
  color: #333;
}

.wm-hint {
  font-size: 0.75rem;
  color: #999;
}

.wm-input {
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  padding: 0.4rem 0.6rem;
  font-size: 0.85rem;
  outline: none;
  transition: border-color 0.2s;
}

.wm-input:focus {
  border-color: #4ccba0;
}

.wm-input-narrow {
  max-width: 160px;
}

.wm-select {
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  padding: 0.4rem 0.6rem;
  font-size: 0.85rem;
  outline: none;
  background: #fff;
  max-width: 240px;
}

.wm-range {
  width: 100%;
  max-width: 300px;
}

.wm-radio-group {
  display: flex;
  gap: 1rem;
}

.wm-radio {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.85rem;
  cursor: pointer;
}

.wm-toggle-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  cursor: pointer;
}

.wm-color-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.wm-color-picker {
  width: 36px;
  height: 32px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  cursor: pointer;
  padding: 2px;
}

.wm-preview-wrap {
  padding: 1rem;
  display: flex;
  justify-content: center;
}

.wm-canvas {
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  max-width: 100%;
  height: auto;
}

.wm-info-box {
  padding: 0 1rem 1rem;
  font-size: 0.8rem;
  color: #666;
  line-height: 1.6;
}

.wm-info-box p {
  margin: 0;
}
</style>