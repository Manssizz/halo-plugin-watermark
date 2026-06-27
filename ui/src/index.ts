import { definePlugin } from "@halo-dev/ui-shared";
import { markRaw } from "vue";
import RiImageEditLine from "~icons/ri/image-edit-line";

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: "ToolsRoot",
      route: {
        path: "watermark",
        name: "WatermarkSettings",
        component: () => import("./views/WatermarkSettings.vue"),
        meta: {
          title: "Image Watermark",
          description:
            "Konfigurasi watermark dan konversi WebP otomatis untuk gambar yang diupload.",
          searchable: true,
          permissions: ["plugin:watermark:manage"],
          menu: {
            name: "Image Watermark",
            icon: markRaw(RiImageEditLine),
            priority: 0,
          },
        },
      },
    },
  ],
});
